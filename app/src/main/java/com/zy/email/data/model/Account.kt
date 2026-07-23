package com.zy.email.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 邮箱账户数据模型
 * 支持两种登录方式：
 * 1. SMTP/IMAP - 适用于QQ邮箱、163邮箱等（用户名+密码/授权码）
 * 2. OAuth2 - 适用于Microsoft、Google等（需要登录验证）
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val email: String,
    val displayName: String = "",
    val password: String,  // SMTP的密码或授权码；OAuth2的refresh_token
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

/**
 * 根据邮箱域名自动识别账户类型
 */
fun detectAccountType(email: String): AccountType {
    val domain = email.substringAfter("@").lowercase()
    return when {
        // Microsoft域名
        domain.contains("microsoft") || 
        domain.contains("outlook") || 
        domain.contains("hotmail") || 
        domain.contains("live") -> AccountType.OAUTH2
        
        // Google域名
        domain.contains("google") || 
        domain.contains("gmail") -> AccountType.OAUTH2
        
        // 默认使用SMTP
        else -> AccountType.SMTP
    }
}

/**
 * 根据账户类型获取默认SMTP配置
 */
fun getSmtpDefaults(email: String): Pair<String, Int> {
    val domain = email.substringAfter("@").lowercase()
    return when {
        domain.contains("qq") -> "smtp.qq.com" to 465
        domain.contains("163") -> "smtp.163.com" to 465
        domain.contains("126") -> "smtp.126.com" to 465
        domain.contains("sina") -> "smtp.sina.com" to 465
        domain.contains("yahoo") -> "smtp.mail.yahoo.com" to 465
        domain.contains("outlook") || domain.contains("hotmail") || domain.contains("live") -> 
            "smtp-mail.outlook.com" to 587
        domain.contains("gmail") -> "smtp.gmail.com" to 465
        domain.contains("foxmail") -> "smtp.qq.com" to 465
        else -> "smtp.example.com" to 465
    }
}

/**
 * 根据账户类型获取默认IMAP配置
 */
fun getImapDefaults(email: String): Pair<String, Int> {
    val domain = email.substringAfter("@").lowercase()
    return when {
        domain.contains("qq") -> "imap.qq.com" to 993
        domain.contains("163") -> "imap.163.com" to 993
        domain.contains("126") -> "imap.126.com" to 993
        domain.contains("sina") -> "imap.sina.com" to 993
        domain.contains("yahoo") -> "imap.mail.yahoo.com" to 993
        domain.contains("outlook") || domain.contains("hotmail") || domain.contains("live") -> 
            "outlook.office365.com" to 993
        domain.contains("gmail") -> "imap.gmail.com" to 993
        domain.contains("foxmail") -> "imap.qq.com" to 993
        else -> "imap.example.com" to 993
    }
}

/**
 * 根据账户类型获取OAuth2配置
 */
fun getOAuth2Defaults(email: String): OAuth2Config? {
    val domain = email.substringAfter("@").lowercase()
    return when {
        domain.contains("outlook") || domain.contains("hotmail") || 
        domain.contains("live") || domain.contains("microsoft") -> OAuth2Config(
            authorizeUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
            tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            scope = "https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send",
            redirectUri = "https://login.microsoftonline.com/common/oauth2/nativeclient"
        )
        domain.contains("gmail") || domain.contains("google") -> OAuth2Config(
            authorizeUrl = "https://accounts.google.com/o/oauth2/v2/auth",
            tokenUrl = "https://oauth2.googleapis.com/token",
            scope = "https://www.googleapis.com/auth/gmail.modify https://www.googleapis.com/auth/gmail.send",
            redirectUri = "http://localhost"
        )
        else -> null
    }
}

data class OAuth2Config(
    val authorizeUrl: String,
    val tokenUrl: String,
    val scope: String,
    val redirectUri: String
)
