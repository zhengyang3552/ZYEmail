package com.zy.email.data.repository

import com.zy.email.data.model.Account
import com.zy.email.utils.EncryptionUtils
import java.util.*
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object SmtpMailSender {
    
    fun sendEmail(account: Account, to: String, subject: String, body: String, isHtml: Boolean = false): SendResult {
        return try {
            val decryptedPassword = EncryptionUtils.decrypt(account.password)
            
            val props = Properties().apply {
                put("mail.smtp.host", account.smtpServer)
                put("mail.smtp.port", account.smtpPort.toString())
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.enable", account.smtpSecure == "SSL")
                put("mail.smtp.starttls.enable", account.smtpSecure == "TLS")
                put("mail.smtp.timeout", "10000")
            }
            
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(account.email, decryptedPassword)
            })
            
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(account.email))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                this.subject = subject
                setContent(body, if (isHtml) "text/html; charset=utf-8" else "text/plain; charset=utf-8")
                sentDate = Date()
            }
            
            Transport.send(message)
            SendResult.Success
        } catch (e: Exception) {
            SendResult.Error(e.message ?: "发送失败")
        }
    }
    
    fun sendEmailWithCc(account: Account, to: String, cc: String?, bcc: String?, subject: String, body: String, isHtml: Boolean = false): SendResult {
        return try {
            val decryptedPassword = EncryptionUtils.decrypt(account.password)
            
            val props = Properties().apply {
                put("mail.smtp.host", account.smtpServer)
                put("mail.smtp.port", account.smtpPort.toString())
                put("mail.smtp.auth", "true")
                put("mail.smtp.ssl.enable", account.smtpSecure == "SSL")
                put("mail.smtp.starttls.enable", account.smtpSecure == "TLS")
                put("mail.smtp.timeout", "10000")
            }
            
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(account.email, decryptedPassword)
            })
            
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(account.email))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                cc?.let { setRecipients(Message.RecipientType.CC, InternetAddress.parse(it)) }
                bcc?.let { setRecipients(Message.RecipientType.BCC, InternetAddress.parse(it)) }
                this.subject = subject
                setContent(body, if (isHtml) "text/html; charset=utf-8" else "text/plain; charset=utf-8")
                sentDate = Date()
            }
            
            Transport.send(message)
            SendResult.Success
        } catch (e: Exception) {
            SendResult.Error(e.message ?: "发送失败")
        }
    }
}

sealed class SendResult {
    object Success : SendResult()
    data class Error(val message: String) : SendResult()
}
