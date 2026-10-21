package com.zy.email.ui.activity

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.zy.email.R
import com.zy.email.data.model.requiresOAuth2ByDefault
import com.zy.email.ui.theme.ThemeManager
import com.zy.email.databinding.ActivityLoginBinding
import com.zy.email.ui.setVisible
import com.zy.email.ui.viewmodel.LoginUiState
import com.zy.email.ui.viewmodel.LoginViewModel
import com.zy.email.utils.EmailUtils
import kotlinx.coroutines.launch

/**
 * 登录页。
 *
 * 两种进入方式：
 *  · 作为启动页：本地已有账户时直接进入主界面（[EXTRA_ADD_MODE] 为 false）
 *  · 从主界面"添加账户"进入：始终停留在表单（[EXTRA_ADD_MODE] 为 true）
 *
 * OAuth2 账户（Outlook / Gmail）在填写 Client ID 后点击登录，会跳转系统
 * 浏览器完成交互式授权；授权回调由 App 内的回环服务器接收，返回登录页后
 * 自动完成令牌交换与收发信验证。
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ADD_MODE = "extra_add_mode"
    }

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    private var addMode = false
    private var useAppPassword = false

    /** 已打开过浏览器的授权地址，避免旋转屏幕等状态重放时重复拉起浏览器 */
    private var launchedAuthorizationUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 动态取色需在视图创建前应用（Android 12+ 且用户开启时生效）
        ThemeManager.applyDynamicColorIfEnabled(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        addMode = intent.getBooleanExtra(EXTRA_ADD_MODE, false)

        setupViews()
        observeState()
        // 入口已由 MainActivity 路由（无账户才会进入本页），直接展示表单；
        // 不再做启动会话检查，避免表单延迟渲染
        viewModel.checkSession(skipWhenAccountsExist = false)
    }

    private fun setupViews() {
        if (addMode) {
            binding.btnCancel.text = getString(R.string.action_cancel)
        }

        binding.editEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) updateTypeHint(binding.editEmail.text?.toString().orEmpty())
        }

        binding.switchAppPassword.setOnCheckedChangeListener { _, isChecked ->
            useAppPassword = isChecked
            updateTypeHint(binding.editEmail.text?.toString().orEmpty())
        }

        binding.btnLogin.setOnClickListener { submit() }
        binding.btnCancel.setOnClickListener {
            // 等待授权期间按钮充当"取消授权"，避免用户只能干等超时
            if (viewModel.state.value is LoginUiState.AwaitingAuthorization) {
                viewModel.cancelAuthorization()
            } else {
                // addMode：返回设置页；无账户进入：返回底下的 MainActivity
                // （其空账户引导页提供"添加账户"入口，行为一致）
                finish()
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state -> render(state) }
        }
    }

    private fun render(state: LoginUiState) {
        val busy = state is LoginUiState.Submitting
        val awaiting = state is LoginUiState.AwaitingAuthorization

        // 启动会话检查（Idle）期间隐藏表单：本地已有账户时会立即跳主界面，
        // 提前渲染表单会造成"登录页闪现后进入主页"的视觉跳动
        binding.formCard.setVisible(state !is LoginUiState.Idle)
        binding.btnCancel.setVisible(state !is LoginUiState.Idle)

        binding.progressBar.setVisible(busy || awaiting)
        binding.btnLogin.isEnabled = !busy && !awaiting
        binding.btnLogin.text = getString(
            when {
                busy -> R.string.login_btn_loading
                awaiting -> R.string.login_oauth_waiting
                isOAuth2Form() -> R.string.login_btn_oauth2
                else -> R.string.login_btn
            }
        )
        binding.editEmail.isEnabled = !busy && !awaiting
        binding.editPassword.isEnabled = !busy && !awaiting
        binding.editDisplayName.isEnabled = !busy && !awaiting
        binding.editClientId.isEnabled = !busy && !awaiting
        binding.switchAppPassword.isEnabled = !busy && !awaiting

        when (state) {
            LoginUiState.Idle, LoginUiState.Ready, LoginUiState.Submitting -> Unit

            LoginUiState.NavigateToMain -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }

            is LoginUiState.Success -> onLoginSuccess()

            is LoginUiState.AwaitingAuthorization -> {
                showHint(state.message, isError = false)
                openAuthorizationPage(state.authorizationUrl)
            }

            is LoginUiState.ClientIdRequired -> {
                binding.tilClientId.error = state.message
                binding.editClientId.requestFocus()
                viewModel.consumeTransientState()
            }

            is LoginUiState.Error -> {
                showHint(state.message, isError = true)
                viewModel.consumeTransientState()
            }
        }
    }

    /** 打开系统浏览器进行授权（Google 拒绝 WebView 中的 OAuth，必须用外部浏览器） */
    private fun openAuthorizationPage(url: String) {
        if (url == launchedAuthorizationUrl) return
        launchedAuthorizationUrl = url
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.login_oauth_no_browser, Toast.LENGTH_LONG).show()
            viewModel.cancelAuthorization()
        }
    }

    private fun onLoginSuccess() {
        // 页面即将关闭，Toast/Snackbar 都来不及展示，改为直接把结果回传给调用方
        setResult(RESULT_OK)

        // addMode：返回设置页；无账户进入：返回底下的 MainActivity
        // （Room 账户 Flow 发射后自动渲染账户列表，无需手动跳转）
        finish()
    }

    private fun isOAuth2Form(): Boolean =
        !useAppPassword && requiresOAuth2ByDefault(binding.editEmail.text?.toString().orEmpty())

    private fun submit() {
        val email = binding.editEmail.text?.toString()?.trim().orEmpty()
        val password = binding.editPassword.text?.toString().orEmpty()
        val displayName = binding.editDisplayName.text?.toString()?.trim().orEmpty()
        val clientId = binding.editClientId.text?.toString()?.trim().orEmpty()
        val oauthLogin = requiresOAuth2ByDefault(email) && !useAppPassword

        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilClientId.error = null

        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.login_error_email_required)
            binding.editEmail.requestFocus()
            return
        }
        if (!EmailUtils.isValidEmail(email)) {
            binding.tilEmail.error = getString(R.string.login_error_email_invalid)
            binding.editEmail.requestFocus()
            return
        }
        if (oauthLogin) {
            if (clientId.isEmpty()) {
                binding.tilClientId.error = getString(R.string.login_client_id_required)
                binding.editClientId.requestFocus()
                return
            }
        } else if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.login_error_password_required)
            binding.editPassword.requestFocus()
            return
        }

        hideKeyboard()
        showHint(null, isError = false)
        viewModel.submit(email, password, displayName, useAppPassword, clientId)
    }

    /**
     * 更新账户类型提示与表单字段可见性。
     *
     * 检测到 Outlook / Gmail 域名时：
     *  · 展示 Client ID 输入框（交互式授权必需）
     *  · 保留"使用应用专用密码登录"开关，作为走 SMTP/IMAP 的替代路径
     */
    private fun updateTypeHint(email: String) {
        if (!email.contains("@")) {
            binding.tvTypeHint.setVisible(false)
            binding.switchAppPassword.setVisible(false)
            binding.tilClientId.setVisible(false)
            return
        }

        val oauthByDefault = requiresOAuth2ByDefault(email)
        binding.switchAppPassword.setVisible(oauthByDefault)
        binding.tilClientId.setVisible(oauthByDefault && !useAppPassword)

        if (oauthByDefault && !useAppPassword) {
            showHint(getString(R.string.login_hint_oauth2), isError = false)
        } else {
            showHint(getString(R.string.login_hint_smtp), isError = false)
        }
    }

    private fun showHint(message: String?, isError: Boolean) {
        if (message.isNullOrBlank()) {
            binding.tvTypeHint.setVisible(false)
            return
        }
        binding.tvTypeHint.text = message
        binding.tvTypeHint.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) R.color.m3_error else R.color.m3_on_surface_variant
            )
        )
        binding.tvTypeHint.setVisible(true)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
