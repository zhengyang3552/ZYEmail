package com.zy.email.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 邮箱账户数据模型
 * 支持两种登录方式：
 * 1. SMTP/IMAP - 适用于QQ邮箱、163邮箱等（用户名+密码/授权码）
 * 2. OAuth2 - 适用于Microsoft、Google等（需要应用注册与授权）
 *
 * 说明：数据模型与数据库版本保持不变（version = 1），
 * 本次改造只调整了默认值推导逻辑，不影响已存储的账户。
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val email: String,
    val displayName: String = "",
    val password: String,  // SMTP的密码或授权码；OAuth2的 access/refresh token
    val accountType: AccountType = AccountType.SMTP,

    // SMTP配置（QQ/163等）
    val smtpServer: String = "",
    val smtpPort: Int = 465,
    val smtpSecure: String = "SSL", // SSL/TLS/None
    val imapServer: String = "",
    val imapPort: Int = 993,
    val imapSecure: String = "SSL",

    // OAuth2配置（Microsoft/Google等）
    val clientId: String = "",
    val clientSecret: String = "",
    val authorizeUrl: String = "",
    val tokenUrl: String = "",
    val scope: String = "",
    val redirectUri: String = "",

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class AccountType {
    SMTP,    // QQ邮箱、163邮箱、企业邮箱等
    OAUTH2   // Microsoft、Google等
}

/** 取邮箱域名；非法输入返回空串（原实现直接用 substringAfter，缺少 @ 时会拿到整串） */
private fun domainOf(email: String): String {
    val trimmed = email.trim()
    val atIndex = trimmed.lastIndexOf('@')
    if (atIndex <= 0 || atIndex == trimmed.length - 1) return ""
    return trimmed.substring(atIndex + 1).lowercase()
}

/**
 * 根据邮箱域名识别账户类型。
 *
 * Microsoft 与 Google 已关闭基础认证，纯密码方式不可用，因此默认标记为 OAuth2；
 * 但两家都支持"应用专用密码"，登录页允许用户显式改用 SMTP/IMAP 方式。
 */
fun detectAccountType(email: String): AccountType {
    val domain = domainOf(email)
    if (domain.isEmpty()) return AccountType.SMTP

    return when {
        domain == "gmail.com" || domain == "googlemail.com" -> AccountType.OAUTH2
        domain.contains("google") || domain.contains("gmail") -> AccountType.OAUTH2
        domain.contains("outlook") ||
                domain.contains("hotmail") ||
                domain.contains("live.") ||
                domain == "live.com" ||
                domain.contains("microsoft") -> AccountType.OAUTH2
        else -> AccountType.SMTP
    }
}

/** 是否属于"默认需要 OAuth2"的服务商（登录页据此展示应用专用密码开关） */
fun requiresOAuth2ByDefault(email: String): Boolean = detectAccountType(email) == AccountType.OAUTH2

/**
 * 根据域名推导默认 SMTP 配置。
 * 未知域名按 `smtp.<域名>` 猜测，比原实现固定返回 smtp.example.com 更可用，
 * 用户仍可在登录页后自行调整。
 */
fun getSmtpDefaults(email: String): Pair<String, Int> {
    val domain = domainOf(email)
    return when {
        domain.isEmpty() -> "smtp.example.com" to 465
        domain.contains("qq") || domain == "foxmail.com" -> "smtp.qq.com" to 465
        domain.contains("163") -> "smtp.163.com" to 465
        domain.contains("126") -> "smtp.126.com" to 465
        domain.contains("sina") -> "smtp.sina.com" to 465
        domain.contains("yahoo") -> "smtp.mail.yahoo.com" to 465
        domain.contains("outlook") || domain.contains("hotmail") || domain.contains("live") ->
            "smtp-mail.outlook.com" to 587
        domain.contains("gmail") || domain.contains("google") -> "smtp.gmail.com" to 465
        else -> "smtp.$domain" to 465
    }
}

/** 根据域名推导默认 IMAP 配置 */
fun getImapDefaults(email: String): Pair<String, Int> {
    val domain = domainOf(email)
    return when {
        domain.isEmpty() -> "imap.example.com" to 993
        domain.contains("qq") || domain == "foxmail.com" -> "imap.qq.com" to 993
        domain.contains("163") -> "imap.163.com" to 993
        domain.contains("126") -> "imap.126.com" to 993
        domain.contains("sina") -> "imap.sina.com" to 993
        domain.contains("yahoo") -> "imap.mail.yahoo.com" to 993
        domain.contains("outlook") || domain.contains("hotmail") || domain.contains("live") ->
            "outlook.office365.com" to 993
        domain.contains("gmail") || domain.contains("google") -> "imap.gmail.com" to 993
        else -> "imap.$domain" to 993
    }
}

/**
 * OAuth2 端点配置。授权地址 / 令牌地址 / scope 由服务商固定，
 * Client ID 由用户在自己的 Azure AD / Google Cloud 应用注册中获取。
 *
 * redirectUriTemplate 中的 {port} 占位符在发起授权时由 OAuth2Manager
 * 用本机回环回调服务器的实际端口替换：
 *  · Microsoft 公共客户端允许 http://localhost 任意端口（需在 Azure 应用中
 *    添加「移动和桌面应用程序」平台并勾选 http://localhost）；
 *  · Google「桌面应用」类型客户端内置支持 http://127.0.0.1 回环重定向。
 */
fun getOAuth2Defaults(email: String, clientId: String = ""): OAuth2Config? {
    val domain = domainOf(email)
    return when {
        domain.contains("outlook") || domain.contains("hotmail") ||
                domain.contains("live") || domain.contains("microsoft") -> OAuth2Config(
            authorizeUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
            tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            scope = "offline_access https://outlook.office.com/IMAP.AccessAsUser.All " +
                    "https://outlook.office.com/SMTP.Send",
            redirectUriTemplate = "http://localhost:{port}/oauth2redirect"
        )
        domain.contains("gmail") || domain.contains("google") -> OAuth2Config(
            authorizeUrl = "https://accounts.google.com/o/oauth2/v2/auth",
            tokenUrl = "https://oauth2.googleapis.com/token",
            scope = "https://mail.google.com/",
            redirectUriTemplate = "http://127.0.0.1:{port}/oauth2redirect"
        )
        else -> null
    }
}

data class OAuth2Config(
    val authorizeUrl: String,
    val tokenUrl: String,
    val scope: String,
    /** 重定向地址模板，{port} 由授权发起时的回调服务器端口替换 */
    val redirectUriTemplate: String
)

/** 把重定向模板中的 {port} 替换为回调服务器实际端口 */
fun resolveRedirectUri(config: OAuth2Config, port: Int): String =
    config.redirectUriTemplate.replace("{port}", port.toString())
