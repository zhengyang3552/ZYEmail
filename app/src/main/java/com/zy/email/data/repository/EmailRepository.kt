package com.zy.email.data.repository

import com.zy.email.data.model.Account
import com.zy.email.data.model.AccountType
import com.zy.email.data.model.detectAccountType
import com.zy.email.data.model.getImapDefaults
import com.zy.email.data.model.getOAuth2Defaults
import com.zy.email.data.model.getSmtpDefaults
import com.zy.email.utils.EncryptionUtils

object EmailRepository {
    
    /**
     * 保存账户
     */
    suspend fun saveAccount(account: Account): Account {
        val encryptedPassword = EncryptionUtils.encrypt(account.password)
        val newAccount = account.copy(
            password = encryptedPassword,
            updatedAt = System.currentTimeMillis()
        )
        
        val existing = RoomDatabaseManager.getAccountByEmail(account.email)
        if (existing != null) {
            RoomDatabaseManager.updateAccount(newAccount)
            return newAccount.copy(id = existing.id)
        } else {
            val id = RoomDatabaseManager.insertAccount(newAccount)
            return newAccount.copy(id = id)
        }
    }
    
    /**
     * 删除账户
     */
    suspend fun deleteAccount(account: Account) {
        RoomDatabaseManager.deleteAccount(account)
    }
    
    /**
     * 获取所有账户
     */
    fun getAllAccounts() = RoomDatabaseManager.getAllAccounts()
    
    /**
     * 登录邮箱并验证配置
     */
    suspend fun loginAccount(account: Account): LoginResult {
        return when (account.accountType) {
            AccountType.SMTP -> loginWithSmtp(account)
            AccountType.OAUTH2 -> loginWithOAuth2(account)
        }
    }
    
    /**
     * SMTP登录（QQ/163等）
     */
    private suspend fun loginWithSmtp(account: Account): LoginResult {
        return try {
            // 验证IMAP连接
            val imapConnected = ImapConnectionManager.verifyConnection(
                server = account.imapServer,
                port = account.imapPort,
                secure = account.imapSecure,
                username = account.email,
                password = EncryptionUtils.decrypt(account.password)
            )
            
            // 验证SMTP连接
            val smtpConnected = SmtpConnectionManager.verifyConnection(
                server = account.smtpServer,
                port = account.smtpPort,
                secure = account.smtpSecure,
                username = account.email,
                password = EncryptionUtils.decrypt(account.password)
            )
            
            if (imapConnected && smtpConnected) {
                LoginResult.Success
            } else {
                LoginResult.Error("连接验证失败")
            }
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "登录失败")
        }
    }
    
    /**
     * OAuth2登录（Microsoft/Google等）
     */
    private suspend fun loginWithOAuth2(account: Account): LoginResult {
        return try {
            // 验证refresh_token
            val refreshed = OAuth2Manager.refreshToken(account)
            if (refreshed) {
                LoginResult.Success
            } else {
                LoginResult.NeedsAuthorization
            }
        } catch (e: Exception) {
            LoginResult.NeedsAuthorization
        }
    }
}

sealed class LoginResult {
    object Success : LoginResult()
    object NeedsAuthorization : LoginResult()
    data class Error(val message: String) : LoginResult()
}
