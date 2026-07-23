package com.zy.email.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zy.email.data.repository.RoomDatabaseManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()
    
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
    
    fun startOAuth2Auth(email: String, password: String, displayName: String) {
        // OAuth2 授权流程 placeholder
        // 实际项目中需要配置 Azure AD 应用
        _loginState.value = LoginState.OAuth2Success
    }
    
    sealed class LoginState {
        object Idle : LoginState()
        object AutoLoginSuccess : LoginState()
        object AutoLoginNoAccounts : LoginState()
        object OAuth2Success : LoginState()
        data class OAuth2Error(val message: String) : LoginState()
    }
}
