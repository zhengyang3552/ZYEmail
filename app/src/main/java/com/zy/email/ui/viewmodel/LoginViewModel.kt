package com.zy.email.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.microsoft.identity.client.*
import com.zy.email.data.model.Account
import com.zy.email.data.model.AccountType
import com.zy.email.data.model.detectAccountType
import com.zy.email.data.model.getImapDefaults
import com.zy.email.data.model.getSmtpDefaults
import com.zy.email.data.repository.EmailRepository
import com.zy.email.data.repository.ImapMailboxManager
import com.zy.email.data.repository.LoginResult
import com.zy.email.data.repository.RoomDatabaseManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 登录界面ViewModel
 */
class LoginViewModel : ViewModel() {
    
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()
    
    /**
     * 启动自动登录 - 检查是否有已保存的账户
     */
    fun startAutoLogin() {
        viewModelScope.launch {
            val accounts = RoomDatabaseManager.getAllAccounts().firstOrNull()
            if (!accounts.isNullOrEmpty()) {
                _loginState.value = LoginState.AutoLoginSuccess
            } else {
                _loginState.value = LoginState.AutoLoginNoAccounts
            }
        }
    }
    
    /**
     * 启动OAuth2授权流程
     */
    fun startOAuth2Auth(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            try {
                val (smtpServer, smtpPort) = getSmtpDefaults(email)
                val (imapServer, imapPort) = getImapDefaults(email)
                
                // Microsoft OAuth2配置
                val account = Account(
                    email = email,
                    displayName = displayName,
                    password = password,
                    accountType = AccountType.OAUTH2,
                    smtpServer = "smtp-mail.outlook.com",
                    smtpPort = 587,
                    smtpSecure = "TLS",
                    imapServer = "outlook.office365.com",
                    imapPort = 993,
                    imapSecure = "SSL",
                    clientId = "your_microsoft_client_id", // 需要在Azure Portal配置
                    authorizeUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
                    tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                    scope = "https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send",
                    redirectUri = "https://login.microsoftonline.com/common/oauth2/nativeclient"
                )
                
                // 保存账户（密码字段暂存refresh_token占位）
                EmailRepository.saveAccount(account)
                
                // 启动Microsoft认证
                val app = PublicClientApplication.createPublicClientApplication(
                    account.clientId,
                    "https://login.microsoftonline.com/common"
                )
                
                val scopes = arrayOf(
                    "https://outlook.office.com/IMAP.AccessAsUser.All",
                    "https://outlook.office.com/SMTP.Send",
                    "openid",
                    "profile"
                )
                
                // 使用交互方式登录
                val authParams = AuthenticationParameters(
                    scopes,
                    null
                )
                
                app.acquirePasswordlessSignIn(
                    email,
                    scopes
                ).enqueue(object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: AuthenticationResult?) {
                        _loginState.value = LoginState.OAuth2Success
                    }
                    
                    override fun onError(exception: MsalException?) {
                        _loginState.value = LoginState.OAuth2Error(exception?.message ?: "授权失败")
                    }
                    
                    override fun onCancel() {
                        _loginState.value = LoginState.OAuth2Error("用户取消授权")
                    }
                })
                
            } catch (e: Exception) {
                _loginState.value = LoginState.OAuth2Error(e.message ?: "启动授权失败")
            }
        }
    }
    
    sealed class LoginState {
        object Idle : LoginState()
        object AutoLoginSuccess : LoginState()
        object AutoLoginNoAccounts : LoginState()
        object OAuth2Success : LoginState()
        data class OAuth2Error(val message: String) : LoginState()
    }
}
