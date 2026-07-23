package com.zy.email.data.repository

import com.sun.mail.smtp.SMTPSSLStore
import com.sun.mail.smtp.SMTPTransport
import java.util.*

object SmtpConnectionManager {
    
    /**
     * 验证SMTP连接
     */
    fun verifyConnection(
        server: String,
        port: Int,
        secure: String,
        username: String,
        password: String
    ): Boolean {
        try {
            val properties = System.getProperties()
            properties["mail.smtp.ssl.enable"] = secure == "SSL"
            properties["mail.smtp.auth"] = "true"
            properties["mail.smtp.starttls.enable"] = secure == "TLS"
            properties["mail.smtp.port"] = port
            
            val transport = SMTPTransport(properties, null, server, port, username, null)
            if (secure == "SSL") {
                val store = SMTPSSLStore(properties, username, password)
                transport.connect()
            } else {
                transport.connect()
                transport.authenticate(username, password)
            }
            transport.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
