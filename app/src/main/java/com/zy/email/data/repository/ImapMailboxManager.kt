package com.zy.email.data.repository

import android.util.Log
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import com.zy.email.data.model.Account
import com.zy.email.utils.EmailUtils
import java.util.Date
import javax.mail.Address
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlin.math.max

/**
 * IMAP 邮件读取。
 *
 * 关键修正（均为原实现中的实际问题）：
 *  1. 附件识别：原实现用 `isMimeType("multipart/…")` 判断附件，导致
 *     multipart/alternative（也就是绝大多数纯正文邮件）全部被标记为"有附件"。
 *     此处改为递归遍历 MIME 结构，识别 disposition=attachment 或带文件名
 *     的部件。
 *  2. 排序：IMAP 返回的是升序，原实现未反转，列表里最新邮件排在最后。
 *  3. 端口/加密方式：统一交给 [ImapConnectionManager]，不再硬编码 SSL。
 *  4. 资源回收：连接与文件夹的关闭放进 `finally`，异常时也不会泄漏。
 *  5. 正文长度、字符集与 HTML 兜底：超长正文截断，纯文本缺失时由 HTML 反推，
 *     避免列表出现"无内容"。
 */
object ImapMailboxManager {

    private const val TAG = "ImapMailboxManager"

    /** 单次最多读取的邮件数，防止首次进入时拉取过多数据 */
    const val DEFAULT_LIMIT = 50

    private const val MAX_TEXT_LENGTH = 20_000
    private const val MAX_HTML_LENGTH = 200_000

    /**
     * 连接账户并读取指定文件夹最近 [limit] 封邮件。
     * 连接失败会抛出异常，由上层统一转换为用户提示。
     */
    suspend fun loadMessages(
        account: Account,
        limit: Int = DEFAULT_LIMIT,
        folder: MailFolder = MailFolder.INBOX
    ): List<EmailMessage> {
        var store: IMAPStore? = null
        var opened: IMAPFolder? = null
        try {
            store = ImapConnectionManager.connect(account)
            opened = openFolder(store, folder)
            return readMessages(opened, limit)
        } finally {
            closeFolder(opened)
            closeStore(store)
        }
    }

    /**
     * 打开目标文件夹。
     * 收件箱固定为 INBOX；已发送文件夹各服务商命名不一（QQ 为 Sent Messages、
     * Gmail 为 [Gmail]/Sent Mail、163 为已发送 等），先按常见名称精确匹配，
     * 再遍历个人命名空间按关键词模糊匹配。
     */
    private fun openFolder(store: IMAPStore, folder: MailFolder): IMAPFolder {
        val target = when (folder) {
            MailFolder.INBOX -> store.getFolder("INBOX")
            MailFolder.SENT -> resolveSentFolder(store)
        }
        if (target == null || !target.exists()) {
            throw IllegalStateException("未找到已发送邮件文件夹，请确认邮箱已开启 IMAP 访问")
        }
        target.open(Folder.READ_ONLY)
        return target as IMAPFolder
    }

    /** 已发送文件夹的常见命名（覆盖 QQ / 163 / 126 / Gmail / Outlook / 新浪等） */
    private val SENT_FOLDER_CANDIDATES = listOf(
        "Sent Messages", "Sent", "Sent Items", "Sent Mail",
        "已发送", "已发送邮件", "已发出的邮件",
        "[Gmail]/Sent Mail", "[Gmail]/已发送邮件"
    )

    private fun resolveSentFolder(store: IMAPStore): Folder? {
        for (name in SENT_FOLDER_CANDIDATES) {
            val candidate = runCatching { store.getFolder(name) }.getOrNull()
            if (candidate?.exists() == true) return candidate
        }

        // 兜底：遍历个人命名空间下的所有文件夹，按名称关键词匹配
        val namespaces = runCatching { store.personalNamespaces }.getOrElse { emptyArray() }
        for (namespace in namespaces) {
            val children = runCatching {
                store.getFolder(namespace.fullName).list()
            }.getOrElse { emptyArray() }
            for (child in children) {
                val simpleName = child.fullName.substringAfterLast(namespace.separator.toString())
                if (simpleName.contains("sent", ignoreCase = true) ||
                    simpleName.contains("已发送")
                ) {
                    return child
                }
            }
        }
        return null
    }

    /**
     * 读取邮件列表（按时间倒序）。
     * 单封解析失败只跳过该封，不影响整页加载。
     */
    fun readMessages(folder: Folder, limit: Int = DEFAULT_LIMIT): List<EmailMessage> {
        val total = runCatching { folder.messageCount }.getOrDefault(0)
        if (total <= 0) return emptyList()

        val start = max(1, total - limit + 1)
        val rawMessages = folder.getMessages(start, total)

        // 一次性拉取信封 / 标记 / 结构，避免逐封触发额外往返
        val profile = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(FetchProfile.Item.FLAGS)
            add(FetchProfile.Item.CONTENT_INFO)
        }
        runCatching { folder.fetch(rawMessages, profile) }

        val result = ArrayList<EmailMessage>(rawMessages.size)
        for (index in rawMessages.indices.reversed()) {
            val message = rawMessages[index]
            try {
                result.add(parseMessage(message))
            } catch (e: Exception) {
                Log.w(TAG, "跳过无法解析的邮件: ${e.message}")
            }
        }
        // 显式按时间倒序：IMAP 序号只反映投递顺序，个别邮件补投或服务器
        // 缓存会导致序号顺序与时间顺序不一致，这里以邮件时间为准统一排序
        // （无时间的邮件排到最后）
        result.sortByDescending { it.date ?: Date(0) }
        return result
    }

    private fun parseMessage(message: Message): EmailMessage {
        val html = extractBody(message, wantHtml = true).take(MAX_HTML_LENGTH)
        var plain = extractBody(message, wantHtml = false).take(MAX_TEXT_LENGTH)
        if (plain.isBlank() && html.isNotBlank()) {
            plain = EmailUtils.htmlToPlainText(html).take(MAX_TEXT_LENGTH)
        }

        return EmailMessage(
            subject = runCatching { message.subject }.getOrNull().orEmpty().trim(),
            from = formatAddresses(runCatching { message.from }.getOrNull()),
            to = formatAddresses(
                runCatching { message.getRecipients(Message.RecipientType.TO) }.getOrNull()
            ),
            cc = formatAddresses(
                runCatching { message.getRecipients(Message.RecipientType.CC) }.getOrNull()
            ),
            date = runCatching { message.sentDate ?: message.receivedDate }.getOrNull(),
            bodyText = plain,
            bodyHtml = html,
            isRead = runCatching { message.getFlags().contains(Flags.Flag.SEEN) }.getOrDefault(false),
            hasAttachments = hasAttachment(message),
            messageId = runCatching { (message as? MimeMessage)?.messageID }.getOrNull()
        )
    }

    /** 递归提取指定类型的正文；找不到时返回空串 */
    private fun extractBody(part: Part, wantHtml: Boolean): String {
        val targetMime = if (wantHtml) "text/html" else "text/plain"
        return try {
            when {
                part.isMimeType(targetMime) -> part.content?.toString().orEmpty()

                part.isMimeType("multipart/*") -> {
                    val multipart = part.content as? Multipart ?: return ""
                    for (index in 0 until multipart.count) {
                        val child = multipart.getBodyPart(index)
                        if (isAttachmentPart(child)) continue
                        val text = extractBody(child, wantHtml)
                        if (text.isNotBlank()) return text
                    }
                    ""
                }

                else -> ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "解析正文失败: ${e.message}")
            ""
        }
    }

    /** 递归判断是否存在真正的附件部件 */
    private fun hasAttachment(part: Part): Boolean {
        return try {
            if (part.isMimeType("multipart/*")) {
                val multipart = part.content as? Multipart ?: return false
                for (index in 0 until multipart.count) {
                    if (hasAttachment(multipart.getBodyPart(index))) return true
                }
                false
            } else {
                isAttachmentPart(part)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isAttachmentPart(part: Part): Boolean {
        return try {
            val disposition = runCatching { part.disposition }.getOrNull()
            val fileName = runCatching { part.fileName }.getOrNull()
            Part.ATTACHMENT.equals(disposition, ignoreCase = true) || !fileName.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    private fun formatAddresses(addresses: Array<Address>?): String {
        if (addresses.isNullOrEmpty()) return ""
        return addresses.joinToString(", ") { address ->
            when (address) {
                is InternetAddress -> {
                    val name = address.personal?.takeIf { it.isNotBlank() }
                    if (name != null) "$name <${address.address.orEmpty()}>"
                    else address.address.orEmpty()
                }
                else -> address.toString()
            }
        }.trim()
    }

    private fun closeFolder(folder: Folder?) {
        if (folder == null) return
        runCatching {
            if (folder.isOpen) folder.close(false)
        }.onFailure { Log.w(TAG, "关闭文件夹失败: ${it.message}") }
    }

    private fun closeStore(store: IMAPStore?) {
        if (store == null) return
        runCatching {
            if (store.isConnected) store.close()
        }.onFailure { Log.w(TAG, "关闭连接失败: ${it.message}") }
    }
}

/**
 * 邮件数据类。
 *
 * 新增 [cc]，用于"全部回复"时正确回填抄送人；原实现只能拿到收件人，
 * "全部回复"实际上等价于"回复"。
 */
data class EmailMessage(
    val subject: String,
    val from: String,
    val to: String,
    val cc: String = "",
    val date: Date?,
    val bodyText: String,
    val bodyHtml: String,
    val isRead: Boolean,
    val hasAttachments: Boolean,
    val messageId: String?
)
