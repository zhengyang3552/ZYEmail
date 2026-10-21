package com.zy.email.ui.viewmodel

import android.app.Application
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
import com.zy.email.utils.ErrorMessages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 登录页状态。
 *
 * 原实现里 OAuth2 分支会直接把状态置为"成功"并跳转主界面——实际上没有发生
 * 任何授权，属于"假成功"。这里改为显式的 [NeedsAppPassword]：明确告知用户
 * 当前版本需要改用应用专用密码，而不是让用户带着不可用的账户进入主界面。
 */
sealed interface LoginUiState {
    data object Idle : LoginUiState

    /** 展示登录表单 */
    data object Ready : LoginUiState

    /** 已有账户，作为启动页时直接进入主界面 */
    data object NavigateToMain : LoginUiState

    data object Submitting : LoginUiState

    data class Success(val account: Account) : LoginUiState

    /** 该邮箱默认需要 OAuth2，当前未内置应用注册信息 */
    data class NeedsAppPassword(val message: String) : LoginUiState

    data class Error(val message: String) : LoginUiState
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = EmailRepository

    private val _state = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var sessionChecked = false

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
     */
    fun submit(email: String, password: String, displayName: String, useAppPassword: Boolean) {
        // 防重复提交：验证过程可能持续数秒，期间按钮已禁用，这里再做一层兜底
        if (_state.value is LoginUiState.Submitting) return

        viewModelScope.launch {
            _state.value = LoginUiState.Submitting

            val accountType = if (useAppPassword) AccountType.SMTP else detectAccountType(email)
            val (smtpServer, smtpPort) = getSmtpDefaults(email)
            val (imapServer, imapPort) = getImapDefaults(email)

            val account = Account(
                email = email,
                displayName = displayName,
                password = password,
                accountType = accountType,
                smtpServer = smtpServer,
                smtpPort = smtpPort,
                smtpSecure = if (smtpPort == 587) "TLS" else "SSL",
                imapServer = imapServer,
                imapPort = imapPort,
                imapSecure = "SSL"
            )

            when (val result = repository.loginAccount(account)) {
                LoginResult.Success -> {
                    try {
                        val saved = repository.saveAccount(account)
                        _state.value = LoginUiState.Success(saved)
                    } catch (e: Exception) {
                        _state.value = LoginUiState.Error(
                            ErrorMessages.from(getApplication(), e)
                        )
                    }
                }

                LoginResult.NeedsAuthorization -> {
                    _state.value = LoginUiState.NeedsAppPassword(
                        getApplication<Application>().getString(R.string.login_oauth_unavailable)
                    )
                }

                is LoginResult.Error -> {
                    val message = result.throwable
                        ?.let { ErrorMessages.from(getApplication(), it) }
                        ?: result.rawMessage
                        ?: getApplication<Application>().getString(R.string.login_failed)
                    _state.value = LoginUiState.Error(message)
                }
            }
        }
    }

    /** 提示类状态消费完毕后回到可再次提交的 Ready 态 */
    fun consumeTransientState() {
        val current = _state.value
        if (current is LoginUiState.Error || current is LoginUiState.NeedsAppPassword) {
            _state.value = LoginUiState.Ready
        }
    }
}
