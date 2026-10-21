package com.zy.email.data.repository

import com.sun.mail.imap.IMAPStore
import com.zy.email.data.model.Account
import com.zy.email.data.model.AccountType
import com.zy.email.utils.EncryptionUtils
import java.util.Properties
import javax.mail.Session

/**
 * IMAP 连接工厂。
 *
 * 相比原实现做了三处修正：
 *  1. 不再直接改写 `System.getProperties()`。原实现把协议参数写进全局属性，
 *     会污染同一进程内的其他会话（SMTP 与 IMAP 互相影响）。
 *  2. 尊重账户自身的 `imapSecure` 配置。原实现无条件启用 SSL，配置为
 *     STARTTLS 或明文时必然连接失败，且错误信息不可读。
 *  3. 补齐连接/读/写超时与主机名校验，避免网络异常时界面长时间无响应。
 *
 * OAuth2 支持：账户类型为 OAUTH2 时，使用 XOAUTH2 SASL 机制认证，
 * 密码参数传入 access token（JavaMail 1.5.5+ 原生支持）；令牌过期时
 * 由 [OAuth2Manager] 自动刷新。
 */
object ImapConnectionManager {

    private const val TIMEOUT_MS = "15000"

    /**
     * 建立 IMAP 连接。失败时抛出 JavaMail 异常，由上层统一转换为用户提示。
     */
    suspend fun connect(account: Account): IMAPStore {
        require(account.imapServer.isNotBlank()) { "IMAP 服务器地址为空" }

        val secure = account.imapSecure.uppercase()
        val useSsl = secure == "SSL"
        val useStartTls = secure == "TLS"
        // 隐式 SSL 使用 imaps 协议，STARTTLS 与明文都走 imap 协议
        val protocol = if (useSsl) "imaps" else "imap"
        val isOAuth2 = account.accountType == AccountType.OAUTH2

        val properties = Properties().apply {
            put("mail.store.protocol", protocol)
            put("mail.$protocol.ssl.enable", useSsl.toString())
            put("mail.$protocol.starttls.enable", useStartTls.toString())
            put("mail.$protocol.ssl.checkserveridentity", "true")
            put("mail.$protocol.connectiontimeout", TIMEOUT_MS)
            put("mail.$protocol.timeout", TIMEOUT_MS)
            put("mail.$protocol.writetimeout", TIMEOUT_MS)
            if (isOAuth2) {
                // XOAUTH2：password 参数即 access token
                put("mail.$protocol.auth.mechanisms", "XOAUTH2")
            }
        }

        val credential = if (isOAuth2) {
            OAuth2Manager.getValidAccessToken(account)
        } else {
            EncryptionUtils.decrypt(account.password)
        }

        val session = Session.getInstance(properties, null)
        val store = session.getStore(protocol) as IMAPStore
        store.connect(account.imapServer, account.imapPort, account.email, credential)
        return store
    }
}
