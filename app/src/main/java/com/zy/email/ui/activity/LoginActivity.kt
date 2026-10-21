package com.zy.email.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
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
 * 原实现把这两种场景混为一谈——启动即调用 startAutoLogin 并提示"已自动登录"，
 * 用户想再添加一个账户就会被立刻弹回主界面。
 */
class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ADD_MODE = "extra_add_mode"
    }

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    private var addMode = false
    private var useAppPassword = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 动态取色需在视图创建前应用（Android 12+ 且用户开启时生效）
        ThemeManager.applyDynamicColorIfEnabled(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        addMode = intent.getBooleanExtra(EXTRA_ADD_MODE, false)

        setupViews()
        observeState()
        viewModel.checkSession(skipWhenAccountsExist = !addMode)
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
        binding.btnCancel.setOnClickListener { finish() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state -> render(state) }
        }
    }

    private fun render(state: LoginUiState) {
        val busy = state is LoginUiState.Submitting

        binding.progressBar.setVisible(busy)
        binding.btnLogin.isEnabled = !busy
        binding.btnLogin.text = getString(
            if (busy) R.string.login_btn_loading else R.string.login_btn
        )
        binding.editEmail.isEnabled = !busy
        binding.editPassword.isEnabled = !busy
        binding.editDisplayName.isEnabled = !busy
        binding.switchAppPassword.isEnabled = !busy

        when (state) {
            LoginUiState.Idle, LoginUiState.Ready, LoginUiState.Submitting -> Unit

            LoginUiState.NavigateToMain -> {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }

            is LoginUiState.Success -> onLoginSuccess()

            is LoginUiState.Error -> {
                showHint(state.message, isError = true)
                viewModel.consumeTransientState()
            }

            is LoginUiState.NeedsAppPassword -> {
                showHint(state.message, isError = true)
                // 提供可操作的下一步：打开"应用专用密码"开关
                binding.switchAppPassword.setVisible(true)
                viewModel.consumeTransientState()
            }
        }
    }

    private fun onLoginSuccess() {
        // 页面即将关闭，Toast/Snackbar 都来不及展示，改为直接把结果回传给调用方
        setResult(RESULT_OK)

        if (!addMode) {
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }

    private fun submit() {
        val email = binding.editEmail.text?.toString()?.trim().orEmpty()
        val password = binding.editPassword.text?.toString().orEmpty()
        val displayName = binding.editDisplayName.text?.toString()?.trim().orEmpty()

        binding.tilEmail.error = null
        binding.tilPassword.error = null

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
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.login_error_password_required)
            binding.editPassword.requestFocus()
            return
        }

        hideKeyboard()
        showHint(null, isError = false)
        viewModel.submit(email, password, displayName, useAppPassword)
    }

    /**
     * 更新账户类型提示。
     *
     * 检测到 Outlook / Gmail 域名时，除了说明默认需要 OAuth2，还会显示
     * "使用应用专用密码登录"开关——这是当前版本下让这类邮箱可用的唯一路径。
     */
    private fun updateTypeHint(email: String) {
        if (!email.contains("@")) {
            binding.tvTypeHint.setVisible(false)
            binding.switchAppPassword.setVisible(false)
            return
        }

        val oauthByDefault = requiresOAuth2ByDefault(email)
        binding.switchAppPassword.setVisible(oauthByDefault)

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
