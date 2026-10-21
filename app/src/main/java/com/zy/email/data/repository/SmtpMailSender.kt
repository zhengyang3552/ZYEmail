package com.zy.email.data.repository

import com.zy.email.data.model.Account
import com.zy.email.data.model.AccountType
import com.zy.email.utils.EncryptionUtils
import java.util.Date
import java.util.Properties
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * SMTP 发送器。
 *
 * 原实现里 `sendEmail` 与 `sendEmailWithCc` 是两份几乎相同的代码，只要改一处
 * 超时配置就会遗漏另一处；这里合并为单一入口，并补齐：
 *  · 连接/读/写超时与主机名校验（避免网络异常时一直转圈）
 *  · 收件人为空时直接返回明确错误，而不是抛出底层异常
 *  · 统一 UTF-8 编码，避免中文主题乱码
 *  · 发送时间、Message-ID 交给 JavaMail 生成
 */
object SmtpMailSender {

    private const val TIMEOUT_MS = "20000"

    /** 构建 SMTP 会话属性（发送与连接验证共用，保证两端配置一致） */
    internal fun propertiesFor(account: Account): Properties {
        val secure = account.smtpSecure.uppercase()
        val useSsl = secure == "SSL"
        val useStartTls = secure == "TLS"

        return Properties().apply {
            put("mail.smtp.host", account.smtpServer)
            put("mail.smtp.port", account.smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.ssl.enable", useSsl.toString())
            put("mail.smtp.starttls.enable", useStartTls.toString())
            // TLS 场景要求必须升级成功，避免静默降级为明文
            put("mail.smtp.starttls.required", useStartTls.toString())
            put("mail.smtp.ssl.checkserveridentity", "true")
            put("mail.smtp.connectiontimeout", TIMEOUT_MS)
            put("mail.smtp.timeout", TIMEOUT_MS)
            put("mail.smtp.writetimeout", TIMEOUT_MS)
            if (account.accountType == AccountType.OAUTH2) {
                // XOAUTH2：connect 的密码参数即 access token
                put("mail.smtp.auth.mechanisms", "XOAUTH2")
            }
        }
    }

    /**
     * 发送邮件。
     *
     * @param to 至少一个收件人；调用方负责先校验地址格式
     */
    suspend fun send(
        account: Account,
        to: List<InternetAddress>,
        cc: List<InternetAddress>,
        bcc: List<InternetAddress>,
        subject: String,
        body: String,
        isHtml: Boolean = false
    ): SendResult {
        if (to.isEmpty()) {
            return SendResult.Error(rawMessage = "收件人为空")
        }

        return try {
            require(account.smtpServer.isNotBlank()) { "SMTP 服务器地址为空" }

            val isOAuth2 = account.accountType == AccountType.OAUTH2
            val credential = if (isOAuth2) {
                OAuth2Manager.getValidAccessToken(account)
            } else {
                EncryptionUtils.decrypt(account.password)
            }

            val session = Session.getInstance(propertiesFor(account), null)
            val from = if (account.displayName.isBlank()) {
                InternetAddress(account.email)
            } else {
                InternetAddress(account.email, account.displayName)
            }

            val message = MimeMessage(session).apply {
                setFrom(from)
                setRecipients(Message.RecipientType.TO, to.toTypedArray())
                if (cc.isNotEmpty()) {
                    setRecipients(Message.RecipientType.CC, cc.toTypedArray())
                }
                if (bcc.isNotEmpty()) {
                    // BCC 收件人由 Transport 在投递时展开，不会写入报文头
                    setRecipients(Message.RecipientType.BCC, bcc.toTypedArray())
                }
                this.subject = subject
                setContent(body, if (isHtml) "text/html; charset=utf-8" else "text/plain; charset=utf-8")
                sentDate = Date()
            }

            // 显式 connect 后发送：原实现用 Transport.send(message)，无凭据
            // 可用（未设置 mail.smtp.user/password 属性），认证必然失败
            val transport = session.getTransport("smtp")
            try {
                transport.connect(account.smtpServer, account.smtpPort, account.email, credential)
                transport.sendMessage(message, message.allRecipients)
            } finally {
                runCatching { transport.close() }
            }
            SendResult.Success
        } catch (e: Exception) {
            SendResult.Error(throwable = e)
        }
    }
}

/** 发送结果 */
sealed interface SendResult {
    data object Success : SendResult
    data class Error(val throwable: Throwable? = null, val rawMessage: String? = null) : SendResult
}
