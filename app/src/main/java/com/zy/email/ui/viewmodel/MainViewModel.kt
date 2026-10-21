package com.zy.email.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zy.email.data.model.Account
import com.zy.email.data.repository.DiskMailCache
import com.zy.email.data.repository.EmailMessage
import com.zy.email.data.repository.EmailRepository
import com.zy.email.data.repository.MailFolder
import com.zy.email.utils.ErrorMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 主界面状态。
 *
 * 原实现用两个裸 StateFlow（accounts / messages）表示界面状态，
 * "加载中""加载失败""空列表"三者无法区分——加载失败与空收件箱在界面上
 * 完全一样。这里用密封接口把五种状态显式建模。
 */
sealed interface MailUiState {
    /** 尚未选择账户 */
    data object Idle : MailUiState

    /** 正在收取邮件 */
    data object Loading : MailUiState

    /** 收取成功且非空 */
    data class Content(val messages: List<EmailMessage>) : MailUiState

    /** 收取成功但收件箱为空 */
    data object Empty : MailUiState

    /** 收取失败（已转换为用户可读文案） */
    data class Error(val message: String) : MailUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmailRepository

    /**
     * 账户列表来自 Room 的 Flow。
     *
     * 初始值用 null 区分"尚未查询到结果"与"确实没有账户"——
     * 若初始值直接给 emptyList()，主界面会先闪现"空账户引导页"，
     * 等 Room 首次发射后才跳到实际内容（启动闪烁的根因）。
     * catch 兜底：数据库异常时退化为空列表，而不是让协程直接崩溃。
     */
    val accounts: StateFlow<List<Account>?> = repository.getAllAccounts()
        .catch { emit(emptyList<Account>()) }
        .map<List<Account>, List<Account>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _mailState = MutableStateFlow<MailUiState>(MailUiState.Idle)
    val mailState: StateFlow<MailUiState> = _mailState.asStateFlow()

    /** 当前查看的账户；0 表示停留在账户列表 */
    var currentAccountId: Long = 0L
        private set

    /** 当前浏览的文件夹（收件箱 / 已发送），供界面切换标签与空态文案 */
    var currentFolder: MailFolder = MailFolder.INBOX
        private set

    private var currentAccount: Account? = null
    private var loadJob: Job? = null

    /** 会话内邮件列表内存缓存（二级为磁盘缓存 DiskMailCache） */
    private val memoryCache = HashMap<String, List<EmailMessage>>()

    private fun cacheKey(): String = "$currentAccountId-${currentFolder.name}"

    fun openAccount(account: Account, folder: MailFolder = MailFolder.INBOX) {
        currentAccountId = account.id
        currentAccount = account
        currentFolder = folder
        loadMessages()
    }

    /** 切换文件夹（收件箱 ⇄ 已发送），保留当前账户重新加载 */
    fun switchFolder(folder: MailFolder) {
        if (folder == currentFolder) return
        currentFolder = folder
        loadMessages()
    }

    /** 下拉刷新 / 菜单刷新：跳过缓存展示，直接重新拉取 */
    fun refresh() {
        if (currentAccount != null) {
            loadMessages(force = true)
        }
    }

    /**
     * 用户打开邮件详情时调用：先乐观更新本地状态（未读点立即消失），
     * 再异步向服务器发送 IMAP 标记；标记失败不影响本次浏览。
     */
    fun markRead(uid: Long) {
        val account = currentAccount ?: return
        if (uid <= 0L) return

        val current = _mailState.value
        if (current is MailUiState.Content) {
            _mailState.value = current.copy(
                messages = current.messages.map {
                    if (it.uid == uid) it.copy(isRead = true) else it
                }
            )
        }

        // 同步两级缓存，避免下次进入（尤其冷启动读磁盘缓存）未读状态回退
        val key = cacheKey()
        val accountId = currentAccountId
        val folder = currentFolder
        memoryCache[key]?.let { list ->
            val updated = list.map {
                if (it.uid == uid) it.copy(isRead = true) else it
            }
            memoryCache[key] = updated
            viewModelScope.launch {
                DiskMailCache.save(getApplication(), accountId, folder, updated)
            }
        }

        viewModelScope.launch {
            repository.markMessageRead(account, folder, uid)
        }
    }

    /** 返回账户列表时清空选择，避免重建后自动跳回收件箱 */
    fun clearSelection() {
        loadJob?.cancel()
        currentAccountId = 0L
        currentAccount = null
        currentFolder = MailFolder.INBOX
        _mailState.value = MailUiState.Idle
    }

    /**
     * 加载当前账户当前文件夹的邮件列表。
     *
     * Stale-While-Revalidate 策略：
     *  · 有缓存（内存 → 磁盘）且非强制刷新时，立即展示缓存内容，随后在
     *    后台静默拉取最新数据，完成后仅在用户仍停留在该账户+文件夹时更新；
     *  · 无缓存或用户主动刷新（force）时，走完整加载态。
     * 已展示缓存时刷新失败静默保留旧内容，仅无缓存时才展示错误态。
     */
    private fun loadMessages(force: Boolean = false) {
        val account = currentAccount ?: return
        val accountId = currentAccountId
        val folder = currentFolder
        val key = cacheKey()

        // 取消上一次请求：快速切换账户/文件夹时避免旧结果覆盖新结果
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val cached = memoryCache[key]
                ?: DiskMailCache.load(getApplication(), accountId, folder)?.also {
                    memoryCache[key] = it
                }

            if (!force && !cached.isNullOrEmpty()) {
                _mailState.value = if (cached.isEmpty()) {
                    MailUiState.Empty
                } else {
                    MailUiState.Content(cached)
                }
            } else {
                _mailState.value = MailUiState.Loading
            }

            try {
                val messages = repository.fetchMessages(account, folder = folder)
                memoryCache[key] = messages
                DiskMailCache.save(getApplication(), accountId, folder, messages)

                // 用户可能已切走，仅停留在原账户+文件夹时才更新界面
                if (currentAccountId == accountId && currentFolder == folder) {
                    _mailState.value = if (messages.isEmpty()) {
                        MailUiState.Empty
                    } else {
                        MailUiState.Content(messages)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 已展示缓存内容时静默失败；无缓存才提示错误
                if (cached.isNullOrEmpty() &&
                    currentAccountId == accountId && currentFolder == folder
                ) {
                    _mailState.value = MailUiState.Error(ErrorMessages.from(getApplication(), e))
                }
            }
        }
    }
}
