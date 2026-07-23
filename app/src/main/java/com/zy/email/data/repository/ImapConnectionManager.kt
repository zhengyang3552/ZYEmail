package com.zy.email.data.repository

import com.sun.mail.imap.IMAPStore
import java.util.*

object ImapConnectionManager {
    
    /**
     * 验证IMAP连接
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
            properties["mail.imap.ssl.enable"] = secure == "SSL"
            properties["mail.imap.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
            properties["mail.imap.port"] = port
            
            val store = IMAPStore(properties)
            store.connect(server, port, username, password)
            store.close()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
