package com.zy.email.utils

import javax.mail.internet.InternetAddress

/**
 * 邮件地址与文本处理工具。
 *
 * 把"地址解析/校验/展示名提取"从各个 Activity 里抽出来，原因是原实现中
 * 收件人只做了一个 `isEmpty` 判断，`InternetAddress.parse` 抛出的
 * AddressException 会直接暴露成 "发送失败"，用户无法知道是哪一项填错了。
 */
object EmailUtils {

    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9._%+\\-']+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$"
    )

    /** 单个地址是否合法（不含显示名） */
    fun isValidAddress(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val trimmed = address.trim()
        if (trimmed.contains(' ') || trimmed.count { it == '@' } != 1) return false
        return EMAIL_REGEX.matches(trimmed)
    }

    /** 整串是否为合法邮箱（用于登录框） */
    fun isValidEmail(email: String?): Boolean = isValidAddress(email)

    /**
     * 把用户输入的收件人串解析成地址列表。
     *
     * 支持以逗号、分号、空白分隔的多个地址，也支持 `张三 <a@b.com>` 形式。
     * 任一地址非法时抛出 [IllegalArgumentException]，并把出错的地址带出去，
     * 便于界面精确定位到具体字段与具体地址。
     */
    fun parseRecipients(raw: String): List<InternetAddress> {
        val normalized = raw.replace(';', ',').trim()
        if (normalized.isEmpty()) return emptyList()

        val parsed = InternetAddress.parse(normalized, true).toList()
        if (parsed.isEmpty()) {
            throw IllegalArgumentException(raw)
        }
        parsed.forEach { address ->
            val value = address.address
            if (!isValidAddress(value)) {
                throw IllegalArgumentException(value ?: raw)
            }
        }
        return parsed
    }

    /** 提取显示名：优先 personal，其次地址本身 */
    fun displayNameOf(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            val address = InternetAddress(raw)
            address.personal?.takeIf { it.isNotBlank() }
                ?: address.address?.takeIf { it.isNotBlank() }
                ?: raw
        } catch (e: Exception) {
            raw
        }
    }

    /** 仅提取地址部分，用于"回复"时回填收件人 */
    fun bareAddressOf(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            InternetAddress(raw).address ?: raw
        } catch (e: Exception) {
            raw
        }
    }

    /** 从可能包含多个地址的串里提取出纯地址列表（用于全部回复） */
    fun bareAddressListOf(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            InternetAddress.parse(raw, false)
                .mapNotNull { it.address?.takeIf { addr -> addr.isNotBlank() } }
                .distinct()
                .joinToString(", ")
        } catch (e: Exception) {
            ""
        }
    }

    /** 头像首字母：中文取首字，英文取大写首字母 */
    fun initialOf(raw: String): String {
        val name = displayNameOf(raw).trim()
        if (name.isEmpty()) return "?"
        val first = name.first()
        return if (first.code < 128) first.uppercaseChar().toString() else first.toString()
    }

    /**
     * 把 HTML 转成用于列表预览的纯文本。
     * 仅做"去标签 + 解实体 + 压缩空白"，不追求完整解析，目的是避免把标签
     * 直接显示在列表里（原实现只用一个正则剥标签，实体和样式内容会残留）。
     */
    fun htmlToPlainText(html: String): String {
        if (html.isBlank()) return ""
        var text = html
        text = text.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        text = text.replace(Regex("(?i)<br\\s*/?>"), " ")
        text = text.replace(Regex("(?i)</(p|div|tr|li|h[1-6])>"), " ")
        text = text.replace(Regex("<[^>]*>"), " ")
        text = text.replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
        return text.replace(Regex("\\s+"), " ").trim()
    }
}
