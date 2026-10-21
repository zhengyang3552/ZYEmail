package com.zy.email.data.repository

import com.zy.email.data.model.Account
import com.zy.email.data.model.AccountType
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
     * 登录校验：SMTP 账户同时验证收信（IMAP）与发信（SMTP）通道，
     * 避免"能收不能发"的账户被当作登录成功。
     */
    suspend fun loginAccount(account: Account): LoginResult = withContext(Dispatchers.IO) {
        when (account.accountType) {
            AccountType.SMTP -> loginWithSmtp(account)
            AccountType.OAUTH2 -> loginWithOAuth2()
        }
    }

    private fun loginWithSmtp(account: Account): LoginResult {
        return try {
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

    /**
     * OAuth2 账户需要应用注册信息（clientId / 重定向 URI）才能走交互式授权，
     * 当前版本未内置，因此统一返回"需要授权"，由界面引导用户改用应用专用密码。
     */
    private fun loginWithOAuth2(): LoginResult = LoginResult.NeedsAuthorization

    /** 拉取收件箱邮件（按时间倒序）。失败时抛出异常，由 ViewModel 转换提示。 */
    suspend fun fetchMessages(
        account: Account,
        limit: Int = ImapMailboxManager.DEFAULT_LIMIT
    ): List<EmailMessage> = withContext(Dispatchers.IO) {
        ImapMailboxManager.loadMessages(account, limit)
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

/** 登录结果 */
sealed interface LoginResult {
    data object Success : LoginResult

    /** 该邮箱需要 OAuth2 授权，当前版本未配置应用注册信息 */
    data object NeedsAuthorization : LoginResult

    data class Error(val throwable: Throwable? = null, val rawMessage: String? = null) : LoginResult
}
