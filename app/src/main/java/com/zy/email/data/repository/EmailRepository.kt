package com.zy.email.data.repository

import com.zy.email.data.model.Account
import com.zy.email.utils.EncryptionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.mail.internet.InternetAddress

/**
 * 邮件数据仓库。
 *
 * 统一约定：本对象中所有需要访问网络或磁盘的挂起函数，都在内部切换到
 * [Dispatchers.IO]。这一点很关键——原实现把 IMAP/SMTP 调用直接放在
 * `lifecycleScope.launch {}`（主线程）里执行，Android 会直接抛出
 * NetworkOnMainThreadException，等于"登录"和"收信"必然失败。
 */
object EmailRepository {

    /**
     * 保存账户（邮箱地址已存在时更新，保留创建时间）。
     * 密码在落库前加密，避免明文写入 Room。
     */
    suspend fun saveAccount(account: Account): Account = withContext(Dispatchers.IO) {
        val prepared = account.copy(
            password = EncryptionUtils.encrypt(account.password),
            updatedAt = System.currentTimeMillis()
        )

        val existing = RoomDatabaseManager.getAccountByEmail(account.email)
        if (existing != null) {
            val updated = prepared.copy(id = existing.id, createdAt = existing.createdAt)
            RoomDatabaseManager.updateAccount(updated)
            updated
        } else {
            val newId = RoomDatabaseManager.insertAccount(prepared)
            prepared.copy(id = newId)
        }
    }

    suspend fun deleteAccount(account: Account) = withContext(Dispatchers.IO) {
        RoomDatabaseManager.deleteAccount(account)
    }

    fun getAllAccounts(): Flow<List<Account>> = RoomDatabaseManager.getAllAccounts()

    suspend fun getAccount(accountId: Long): Account? = withContext(Dispatchers.IO) {
        RoomDatabaseManager.getAccountById(accountId)
    }

    /** 用于"未指定账户"时的兜底：取最近添加的账户 */
    suspend fun getFirstAccount(): Account? = withContext(Dispatchers.IO) {
        RoomDatabaseManager.getAllAccounts().first().firstOrNull()
    }

    /**
     * 登录校验：同时验证收信（IMAP）与发信（SMTP）通道，
     * 避免"能收不能发"的账户被当作登录成功。
     *
     * SMTP 账户用密码 / 授权码认证；OAUTH2 账户用 XOAUTH2 机制认证，
     * 令牌过期时由 [OAuth2Manager] 自动刷新。
     */
    suspend fun loginAccount(account: Account): LoginResult = withContext(Dispatchers.IO) {
        try {
            // 先验证收件通道
            ImapConnectionManager.connect(account).let { store ->
                runCatching { if (store.isConnected) store.close() }
            }
            // 再验证发件通道
            SmtpConnectionManager.verify(account)
            LoginResult.Success
        } catch (e: Exception) {
            LoginResult.Error(e)
        }
    }

    /** 拉取指定文件夹的邮件（收件箱 / 已发送），按时间倒序。失败时抛出异常，由 ViewModel 转换提示。 */
    suspend fun fetchMessages(
        account: Account,
        limit: Int = ImapMailboxManager.DEFAULT_LIMIT,
        folder: MailFolder = MailFolder.INBOX
    ): List<EmailMessage> = withContext(Dispatchers.IO) {
        ImapMailboxManager.loadMessages(account, limit, folder)
    }

    /**
     * 修改账户显示名称（登录后随时可改）。
     *
     * 只更新 displayName 字段，密码 / 令牌等其余字段原样保留；
     * 写入 Room 后，依赖 getAllAccounts Flow 的界面（主界面账户列表、
     * 设置页账户列表）会自动刷新，写信页的发件人名称也会随之生效。
     */
    suspend fun updateDisplayName(account: Account, displayName: String): Account =
        withContext(Dispatchers.IO) {
            val updated = account.copy(
                displayName = displayName.trim(),
                updatedAt = System.currentTimeMillis()
            )
            RoomDatabaseManager.updateAccount(updated)
            updated
        }

    /** 发送邮件。地址解析与校验由调用方完成。 */
    suspend fun sendMail(
        account: Account,
        to: List<InternetAddress>,
        cc: List<InternetAddress> = emptyList(),
        bcc: List<InternetAddress> = emptyList(),
        subject: String,
        body: String,
        isHtml: Boolean = false
    ): SendResult = withContext(Dispatchers.IO) {
        SmtpMailSender.send(
            account = account,
            to = to,
            cc = cc,
            bcc = bcc,
            subject = subject,
            body = body,
            isHtml = isHtml
        )
    }
}

/** 邮箱文件夹 */
enum class MailFolder {
    /** 收件箱 */
    INBOX,

    /** 已发送（由 ImapMailboxManager 按服务商命名自动解析） */
    SENT
}

/** 登录结果 */
sealed interface LoginResult {
    data object Success : LoginResult

    data class Error(val throwable: Throwable? = null, val rawMessage: String? = null) : LoginResult
}
