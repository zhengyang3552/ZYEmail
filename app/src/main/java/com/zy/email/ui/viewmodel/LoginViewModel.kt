package com.zy.email.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zy.email.R
import com.zy.email.data.model.Account
import com.zy.email.data.model.AccountType
import com.zy.email.data.model.detectAccountType
import com.zy.email.data.model.getImapDefaults
import com.zy.email.data.model.getSmtpDefaults
import com.zy.email.data.repository.EmailRepository
import com.zy.email.data.repository.LoginResult
import com.zy.email.data.repository.OAuth2AuthorizationTimeoutException
import com.zy.email.data.repository.OAuth2Manager
import com.zy.email.data.repository.OAuth2RedirectResult
import com.zy.email.utils.ErrorMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 登录页状态。
 *
 * OAuth2 账户走真正的交互式授权：填入 Client ID 后跳转浏览器完成授权，
 * 回调结果自动换取令牌并验证收发信通道。
 */
sealed interface LoginUiState {
    data object Idle : LoginUiState

    /** 展示登录表单 */
    data object Ready : LoginUiState

    /** 已有账户，作为启动页时直接进入主界面 */
    data object NavigateToMain : LoginUiState

    data object Submitting : LoginUiState

    data class Success(val account: Account) : LoginUiState

    /** OAuth2 登录缺少 Client ID，需要在表单中补填 */
    data class ClientIdRequired(val message: String) : LoginUiState

    /** 已跳转浏览器等待用户完成 OAuth2 授权 */
    data class AwaitingAuthorization(
        val authorizationUrl: String,
        val message: String
    ) : LoginUiState

    data class Error(val message: String) : LoginUiState
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "LoginViewModel"
    }

    private val repository = EmailRepository

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var sessionChecked = false
    private var oauthJob: Job? = null

    /**
     * 检查本地是否已有账户。
     *
     * @param skipWhenAccountsExist 作为启动页时为 true（直接进主界面）；
     *        从主界面"添加账户"进入时为 false（始终停留在表单）
     */
    fun checkSession(skipWhenAccountsExist: Boolean) {
        if (sessionChecked) return
        sessionChecked = true

        if (!skipWhenAccountsExist) {
            _state.value = LoginUiState.Ready
            return
        }

        viewModelScope.launch {
            val existing = try {
                repository.getFirstAccount()
            } catch (e: Exception) {
                null
            }
            _state.value = if (existing != null) LoginUiState.NavigateToMain else LoginUiState.Ready
        }
    }

    /**
     * 提交登录。
     *
     * @param useAppPassword 用户显式选择"使用应用专用密码"时强制走 SMTP/IMAP
     * @param clientId OAuth2 授权所需的应用注册 Client ID（Azure AD / Google Cloud）
     */
    fun submit(
        email: String,
        password: String,
        displayName: String,
        useAppPassword: Boolean,
        clientId: String
    ) {
        // 防重复提交：验证过程可能持续数秒，期间按钮已禁用，这里再做一层兜底
        if (_state.value is LoginUiState.Submitting || _state.value is LoginUiState.AwaitingAuthorization) return

        val accountType = if (useAppPassword) AccountType.SMTP else detectAccountType(email)

        if (accountType == AccountType.OAUTH2) {
            if (clientId.isBlank()) {
                _state.value = LoginUiState.ClientIdRequired(
                    getApplication<Application>().getString(R.string.login_client_id_required)
                )
                return
            }
            startOAuth2Flow(email, displayName, clientId.trim())
        } else {
            submitSmtpLogin(email, password, displayName)
        }
    }

    private fun submitSmtpLogin(email: String, password: String, displayName: String) {
        if (_state.value is LoginUiState.Submitting) return

        viewModelScope.launch {
            _state.value = LoginUiState.Submitting

            val (smtpServer, smtpPort) = getSmtpDefaults(email)
            val (imapServer, imapPort) = getImapDefaults(email)

            val account = Account(
                email = email,
                displayName = displayName,
                password = password,
                accountType = AccountType.SMTP,
                smtpServer = smtpServer,
                smtpPort = smtpPort,
                smtpSecure = if (smtpPort == 587) "TLS" else "SSL",
                imapServer = imapServer,
                imapPort = imapPort,
                imapSecure = "SSL"
            )

            when (val result = repository.loginAccount(account)) {
                LoginResult.Success -> saveAccount(account)
                is LoginResult.Error -> _state.value = LoginUiState.Error(
                    result.throwable
                        ?.let { ErrorMessages.from(getApplication(), it) }
                        ?: result.rawMessage
                        ?: getApplication<Application>().getString(R.string.login_failed)
                )
            }
        }
    }

    /**
     * OAuth2 交互式授权完整流程：
     * 发起授权（起回调服务器）→ 打开浏览器 → 等待回调 → 换取令牌 →
     * 验证收发信通道 → 保存账户。
     */
    private fun startOAuth2Flow(email: String, displayName: String, clientId: String) {
        oauthJob?.cancel()
        oauthJob = viewModelScope.launch {
            try {
                _state.value = LoginUiState.Submitting

                val start = OAuth2Manager.startAuthorization(email, clientId, displayName)
                _state.value = LoginUiState.AwaitingAuthorization(
                    authorizationUrl = start.authorizationUrl,
                    message = getApplication<Application>().getString(R.string.login_oauth_authorizing)
                )

                when (val redirect = OAuth2Manager.awaitAuthorizationResult()) {
                    is OAuth2RedirectResult.Denied -> {
                        _state.value = LoginUiState.Error(
                            getApplication<Application>().getString(
                                R.string.login_oauth_denied,
                                redirect.errorDescription ?: redirect.error
                            )
                        )
                        return@launch
                    }

                    is OAuth2RedirectResult.Authorized -> {
                        _state.value = LoginUiState.Submitting
                        val (account, _) = OAuth2Manager.exchangeAuthorizationCode(redirect.code)

                        when (val result = repository.loginAccount(account)) {
                            LoginResult.Success -> saveAccount(account)
                            is LoginResult.Error -> _state.value = LoginUiState.Error(
                                getApplication<Application>().getString(R.string.login_oauth_verify_failed) +
                                        (result.throwable?.let { "\n${ErrorMessages.from(getApplication(), it)}" } ?: "")
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OAuth2AuthorizationTimeoutException) {
                _state.value = LoginUiState.Error(e.message ?: "授权超时")
            } catch (e: Exception) {
                Log.w(TAG, "OAuth2 授权流程失败", e)
                _state.value = LoginUiState.Error(
                    ErrorMessages.from(getApplication(), e)
                )
            } finally {
                OAuth2Manager.cancelAuthorization()
            }
        }
    }

    /** 校验通过后落库（密码 / 令牌在 Repository 内统一加密） */
    private suspend fun saveAccount(account: Account) {
        try {
            val saved = repository.saveAccount(account)
            _state.value = LoginUiState.Success(saved)
        } catch (e: Exception) {
            _state.value = LoginUiState.Error(ErrorMessages.from(getApplication(), e))
        }
    }

    /** 用户主动取消授权 */
    fun cancelAuthorization() {
        val current = _state.value
        if (current is LoginUiState.AwaitingAuthorization || current is LoginUiState.Submitting) {
            oauthJob?.cancel()
            oauthJob = null
            OAuth2Manager.cancelAuthorization()
            _state.value = LoginUiState.Ready
        }
    }

    /** 提示类状态消费完毕后回到可再次提交的 Ready 态 */
    fun consumeTransientState() {
        val current = _state.value
        if (current is LoginUiState.Error || current is LoginUiState.ClientIdRequired) {
            _state.value = LoginUiState.Ready
        }
    }

    override fun onCleared() {
        OAuth2Manager.cancelAuthorization()
        super.onCleared()
    }
}
