package com.zy.email.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zy.email.data.model.Account
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
     * 账户列表来自 Room 的 Flow，插入/删除后自动刷新界面。
     * catch 兜底：数据库异常时退化为空列表，而不是让协程直接崩溃。
     */
    val accounts: StateFlow<List<Account>> = repository.getAllAccounts()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** 下拉刷新 / 重试 */
    fun refresh() {
        if (currentAccount != null) {
            loadMessages()
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

    private fun loadMessages() {
        val account = currentAccount ?: return
        // 取消上一次请求：快速切换账户/文件夹时避免旧结果覆盖新结果
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _mailState.value = MailUiState.Loading
            try {
                val messages = repository.fetchMessages(account, folder = currentFolder)
                _mailState.value = if (messages.isEmpty()) {
                    MailUiState.Empty
                } else {
                    MailUiState.Content(messages)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mailState.value = MailUiState.Error(ErrorMessages.from(getApplication(), e))
            }
        }
    }
}
