package com.zy.email.data.repository

import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.*
import com.zy.email.data.model.Account
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Microsoft OAuth2认证管理
 * 用于Microsoft/Outlook/Hotmail等邮箱的登录验证
 */
object OAuth2Manager {
    
    private const val AUTHORITY = "https://login.microsoftonline.com/common"
    private const val REDIRECT_URI = "https://login.microsoftonline.com/common/oauth2/nativeclient"
    
    /**
     * 启动Microsoft OAuth2认证流程
     * 返回授权结果Flow
     */
    fun authenticate(account: Account): Flow<AuthenticationResult> = callbackFlow {
        val interactiveAccount = PublicClientApplication.createPublicClientApplication(
            account.clientId,
            AUTHORITY
        )
        
        val scopes = arrayOf(
            "https://outlook.office.com/IMAP.AccessAsUser.All",
            "https://outlook.office.com/SMTP.Send",
            "openid",
            "profile"
        )
        
        interactiveAccount.acquirePasswordlessSignIn(
            account.email,
            scopes
        ).enqueue()
        
        awaitClose {
            // Cleanup
        }
    }
    
    /**
     * 使用refresh token刷新访问令牌
     */
    suspend fun refreshToken(account: Account): Boolean {
        return try {
            val application = PublicClientApplication.createPublicClientApplication(
                account.clientId,
                AUTHORITY
            )
            
            // 查找已保存的账户
            val signInResult = application.getAccounts().await()
            
            if (signInResult.size > 0) {
                val silentParams = SilentParameters.builder(
                    signInResult[0],
                    arrayOf(
                        "https://outlook.office.com/IMAP.AccessAsUser.All",
                        "https://outlook.office.com/SMTP.Send"
                    )
                ).build()
                
                val silentResult = application.acquireSilent(silentParams).await()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 清除所有已保存的账户
     */
    suspend fun clearAccounts(): Boolean {
        return try {
            val application = PublicClientApplication.createPublicClientApplication(
                "client_id_placeholder",
                AUTHORITY
            )
            application.removeAccount().await()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
