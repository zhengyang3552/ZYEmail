package com.zy.email.utils

import android.content.Context
import com.zy.email.R
import java.util.Locale

/**
 * 把底层异常翻译成用户能理解、且能据此行动的提示。
 *
 * 原实现把所有异常统一塞进 Toast（`e.message ?: "登录失败"`），用户看到的
 * 往往是 `javax.mail.AuthenticationFailedException` 之类对排查毫无帮助的文本。
 * 这里按"先精确后宽泛"的顺序匹配，并沿 cause 链向下查找 3 层，
 * 因为 JavaMail 习惯把真正的 Socket/SSL 异常包在 MessagingException 里。
 */
object ErrorMessages {

    private const val MAX_DEPTH = 4

    fun from(context: Context, throwable: Throwable?): String {
        if (throwable == null) return context.getString(R.string.error_generic)

        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < MAX_DEPTH) {
            match(context, current)?.let { return it }
            current = current.cause
            depth++
        }

        val raw = throwable.message?.trim()
        return if (!raw.isNullOrEmpty()) {
            context.getString(R.string.error_unknown, raw)
        } else {
            context.getString(R.string.error_generic)
        }
    }

    private fun match(context: Context, throwable: Throwable): String? {
        val type = throwable.javaClass.name
        val message = throwable.message?.lowercase(Locale.ROOT).orEmpty()

        return when {
            // 认证失败：JavaMail 的 AuthenticationFailedException，或服务器返回 535
            type.contains("AuthenticationFailed") ||
                    message.contains("authentication") ||
                    message.contains("535") ||
                    message.contains("invalid credentials") ||
                    message.contains("username and password not accepted") ||
                    message.contains("login fail") ->
                context.getString(R.string.error_auth_failed)

            // 域名解析失败
            type.contains("UnknownHost") || message.contains("unknownhost") ->
                context.getString(R.string.error_unknown_host)

            // 连接被拒绝（通常是端口写错或服务器未开对应服务）
            message.contains("connection refused") || message.contains("econnrefused") ->
                context.getString(R.string.error_connection_refused)

            // 超时
            type.contains("SocketTimeout") ||
                    message.contains("timed out") ||
                    message.contains("timeout") ->
                context.getString(R.string.error_timeout)

            // TLS/证书问题（例如用 465 端口却配置成 STARTTLS）
            type.contains("SSL") ||
                    type.contains("CertPath") ||
                    message.contains("ssl") ||
                    message.contains("certificate") ||
                    message.contains("handshake") ->
                context.getString(R.string.error_ssl)

            // 其他网络层问题
            type.contains("SocketException") ||
                    type.contains("ConnectException") ||
                    type.contains("UnknownService") ||
                    message.contains("network is unreachable") ||
                    message.contains("no route to host") ->
                context.getString(R.string.error_network)

            else -> null
        }
    }
}
