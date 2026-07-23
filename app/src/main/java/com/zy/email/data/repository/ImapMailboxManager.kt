package com.zy.email.data.repository

import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import com.zy.email.data.model.Account
import com.zy.email.data.model.EncryptionUtils
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import java.util.*
import kotlin.math.max

/**
 * IMAP邮件收取管理器
 * 负责从服务器读取邮件
 */
object ImapMailboxManager {
    
    /**
     * 获取IMAP Store
     */
    fun getStore(account: Account): IMAPStore? {
        return try {
            val decryptedPassword = EncryptionUtils.decrypt(account.password)
            val properties = Properties()
            properties["mail.imap.ssl.enable"] = "true"
            properties["mail.imap.auth.plain.disable"] = "true"
            
            val store = IMAPStore(properties)
            store.connect(
                account.imapServer,
                account.imapPort,
                account.email,
                decryptedPassword
            )
            store
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 获取文件夹
     */
    fun getFolder(store: IMAPStore, folderName: String = "INBOX"): IMAPFolder? {
        return try {
            val folder = store.getFolder(folderName)
            folder.open(Folder.READ_ONLY)
            folder as? IMAPFolder
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 获取邮件列表
     */
    fun getMessages(folder: Folder, maxCount: Int = 50): List<EmailMessage> {
        val messages = mutableListOf<EmailMessage>()
        
        try {
            val totalMessages = folder.messageCount
            val start = max(1, totalMessages - maxCount + 1)
            val end = totalMessages
            
            if (start > end) return messages
            
            val msgArray = folder.getMessages(start, end)
            
            for (msg in msgArray) {
                try {
                    messages.add(parseMessage(msg))
                } catch (e: Exception) {
                    // Skip unparseable messages
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return messages
    }
    
    /**
     * 解析邮件
     */
    private fun parseMessage(msg: Message): EmailMessage {
        return EmailMessage(
            subject = msg.subject ?: "",
            from = formatAddress(msg.from),
            to = formatAddress(msg.replyTo ?: msg.recipients(Message.RecipientType.TO)),
            date = msg.sentDate,
            bodyText = getTextContent(msg, false),
            bodyHtml = getTextContent(msg, true),
            isRead = isMessageRead(msg),
            hasAttachments = msg.isMimeType("multipart/*"),
            messageId = msg.messageID
        )
    }
    
    /**
     * 格式化地址
     */
    private fun formatAddress(addresses: Array<*>): String {
        if (addresses.isNullOrEmpty()) return ""
        val addressesList = addresses.toList() as? List<*> ?: return ""
        
        return addressesList.joinToString(", ") { addr ->
            when (addr) {
                is jakarta.mail.internet.InternetAddress -> {
                    if (addr.name.isNotEmpty()) "${addr.name} <${addr.address}>"
                    else addr.address ?: ""
                }
                else -> addr.toString()
            }
        }
    }
    
    /**
     * 获取邮件正文
     */
    private fun getTextContent(msg: Message, html: Boolean): String {
        return try {
            when {
                html && msg.isMimeType("text/html") -> msg.content.toString()
                !html && msg.isMimeType("text/plain") -> msg.content.toString()
                msg.isMimeType("multipart/*") -> {
                    val mp = msg.content as jakarta.mail.multipart.Multipart
                    var result = ""
                    for (i in 0 until mp.count) {
                        val bodyPart = mp.getBodyPart(i)
                        when {
                            html && bodyPart.isMimeType("text/html") -> {
                                result = bodyPart.content.toString()
                                break
                            }
                            !html && bodyPart.isMimeType("text/plain") -> {
                                result = bodyPart.content.toString()
                                break
                            }
                            bodyPart.isMimeType("multipart/*") -> {
                                val nestedMp = bodyPart.content as jakarta.mail.multipart.Multipart
                                for (j in 0 until nestedMp.count) {
                                    val nestedPart = nestedMp.getBodyPart(j)
                                    if (nestedPart.isMimeType("text/html")) {
                                        result = nestedPart.content.toString()
                                        break
                                    }
                                }
                                if (result.isNotEmpty()) break
                            }
                        }
                    }
                    if (result.isEmpty() && !html) {
                        msg.content.toString()
                    } else {
                        result
                    }
                }
                else -> msg.content.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
    
    /**
     * 检查邮件是否已读
     */
    private fun isMessageRead(msg: Message): Boolean {
        return try {
            val flags = msg.flags
            flags.contains(Flags.Flag.SEEN)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 关闭文件夹
     */
    fun closeFolder(folder: Folder, expunge: Boolean = false) {
        try {
            folder.close(expunge)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 关闭Store
     */
    fun closeStore(store: IMAPStore) {
        try {
            store.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * 邮件数据类
 */
data class EmailMessage(
    val subject: String,
    val from: String,
    val to: String,
    val date: Date?,
    val bodyText: String,
    val bodyHtml: String,
    val isRead: Boolean,
    val hasAttachments: Boolean,
    val messageId: String?
)
