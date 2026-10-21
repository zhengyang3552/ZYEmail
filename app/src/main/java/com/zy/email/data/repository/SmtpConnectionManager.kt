package com.zy.email.data.repository

import com.zy.email.data.model.Account
import com.zy.email.utils.EncryptionUtils
import javax.mail.Session
import javax.mail.Transport

/**
 * SMTP 连接验证。
 *
 * 原实现的问题：SSL 分支只 new 了一个 SMTPSSLStore 却从未真正认证，
 * 于是"验证通过"并不能证明账号密码正确，用户会在发送时才失败。
 * 这里统一走 `Session.getTransport("smtp")` + `connect(host, port, user, pwd)`，
 * SSL / STARTTLS 只通过属性区分，认证过程完全一致。
 */
object SmtpConnectionManager {

    /**
     * 验证 SMTP 能否成功登录。失败时抛出异常，由上层转换为用户提示。
     */
    fun verify(account: Account) {
        require(account.smtpServer.isNotBlank()) { "SMTP 服务器地址为空" }

        val session = Session.getInstance(SmtpMailSender.propertiesFor(account), null)
        val transport = session.getTransport("smtp")
        try {
            transport.connect(
                account.smtpServer,
                account.smtpPort,
                account.email,
                EncryptionUtils.decrypt(account.password)
            )
        } finally {
            runCatching { transport.close() }
        }
    }
}
