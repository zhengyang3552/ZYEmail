package com.zy.email.data.repository

import com.sun.mail.smtp.SMTPSSLStore
import com.sun.mail.smtp.SMTPTransport
import com.zy.email.data.model.Account
import com.zy.email.utils.EncryptionUtils
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.*

/**
 * SMTP邮件发送管理器
 * 负责发送邮件
 */
object SmtpMailSender {
    
    /**
     * 发送邮件
     */
    fun sendEmail(account: Account, to: String, subject: String, body: String, isHtml: Boolean = false): SendResult {
        return try {
            val decryptedPassword = EncryptionUtils.decrypt(account.password)
            
            val properties = Properties()
            properties["mail.smtp.host"] = account.smtpServer
            properties["mail.smtp.port"] = account.smtpPort.toString()
            properties["mail.smtp.auth"] = "true"
            properties["mail.smtp.ssl.enable"] = account.smtpSecure == "SSL"
            properties["mail.smtp.starttls.enable"] = account.smtpSecure == "TLS"
            properties["mail.smtp.timeout"] = "10000"
            properties["mail.smtp.connectiontimeout"] = "10000"
            properties["mail.smtp.writetimeout"] = "10000"
            
            val session = Session.getInstance(properties)
            // session.setDebug(true) // Debug模式
            
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(account.email))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            message.subject = subject
            
            if (isHtml) {
                message.setContent(body, "text/html; charset=utf-8")
            } else {
                message.text = body
            }
            
            message.sentDate = Date()
            message.saveChanges()
            
            val transport = SMTPTransport(session, null, account.smtpServer, account.smtpPort, null, null)
            transport.connect()
            transport.sendMessage(message, message.getAllRecipients())
            transport.close()
            
            SendResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            SendResult.Error(e.message ?: "发送失败")
        }
    }
    
    /**
     * 发送邮件（带抄送和密送）
     */
    fun sendEmailWithCc(
        account: Account,
        to: String,
        cc: String? = null,
        bcc: String? = null,
        subject: String,
        body: String,
        isHtml: Boolean = false
    ): SendResult {
        return try {
            val decryptedPassword = EncryptionUtils.decrypt(account.password)
            
            val properties = Properties()
            properties["mail.smtp.host"] = account.smtpServer
            properties["mail.smtp.port"] = account.smtpPort.toString()
            properties["mail.smtp.auth"] = "true"
            properties["mail.smtp.ssl.enable"] = account.smtpSecure == "SSL"
            properties["mail.smtp.starttls.enable"] = account.smtpSecure == "TLS"
            properties["mail.smtp.timeout"] = "10000"
            properties["mail.smtp.connectiontimeout"] = "10000"
            properties["mail.smtp.writetimeout"] = "10000"
            
            val session = Session.getInstance(properties)
            
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(account.email))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            
            cc?.let {
                message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(it))
            }
            
            bcc?.let {
                message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(it))
            }
            
            message.subject = subject
            
            if (isHtml) {
                message.setContent(body, "text/html; charset=utf-8")
            } else {
                message.text = body
            }
            
            message.sentDate = Date()
            message.saveChanges()
            
            val transport = SMTPTransport(session, null, account.smtpServer, account.smtpPort, null, null)
            transport.connect()
            transport.sendMessage(message, message.getAllRecipients())
            transport.close()
            
            SendResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            SendResult.Error(e.message ?: "发送失败")
        }
    }
}

sealed class SendResult {
    object Success : SendResult()
    data class Error(val message: String) : SendResult()
}
