package com.zy.email.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.zy.email.databinding.ActivityLoginBinding
import com.zy.email.data.model.Account
import com.zy.email.data.model.AccountType
import com.zy.email.data.model.detectAccountType
import com.zy.email.data.model.getImapDefaults
import com.zy.email.data.model.getSmtpDefaults
import com.zy.email.data.repository.EmailRepository
import com.zy.email.data.repository.LoginResult
import com.zy.email.ui.viewmodel.LoginViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 登录界面
 * 支持两种登录方式：
 * 1. 手动配置 - 适用于QQ/163等SMTP邮箱
 * 2. 一键登录 - 适用于Microsoft/Google等OAuth2邮箱
 */
class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
        observeLoginState()
        
        // 检查是否已有登录的账户
        viewModel.startAutoLogin()
    }
    
    private fun setupViews() {
        // 邮箱地址变更时自动检测账户类型
        binding.editEmail.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            val email = binding.editEmail.text.toString().trim()
            if (email.contains("@")) {
                val accountType = detectAccountType(email)
                updateAccountTypeUI(accountType)
            }
        }
        
        // 登录按钮
        binding.btnLogin.setOnClickListener {
            val email = binding.editEmail.text.toString().trim()
            val password = binding.editPassword.text.toString().trim()
            val displayName = binding.editDisplayName.text.toString().trim()
            
            if (email.isEmpty()) {
                binding.editEmail.error = "请输入邮箱地址"
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.editEmail.error = "邮箱地址格式不正确"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                binding.editPassword.error = "请输入密码或授权码"
                return@setOnClickListener
            }
            
            performLogin(email, password, displayName)
        }
        
        // 取消按钮
        binding.btnCancel.setOnClickListener {
            finish()
        }
    }
    
    /**
     * 根据账户类型更新UI
     */
    private fun updateAccountTypeUI(accountType: AccountType) {
        when (accountType) {
            AccountType.OAUTH2 -> {
                // 显示OAuth2相关提示
                binding.tvTypeHint.text = "检测到Microsoft/Google邮箱，将使用安全登录方式"
                binding.tvTypeHint.visibility = View.VISIBLE
            }
            AccountType.SMTP -> {
                // 显示SMTP配置选项
                binding.tvTypeHint.text = "请输入QQ/163等邮箱的密码或授权码"
                binding.tvTypeHint.visibility = View.VISIBLE
            }
        }
    }
    
    /**
     * 执行登录
     */
    private fun performLogin(email: String, password: String, displayName: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false
        
        lifecycleScope.launch {
            val accountType = detectAccountType(email)
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
            
            val result = EmailRepository.loginAccount(account)
            
            when (result) {
                is LoginResult.Success -> {
                    // 保存账户
                    val savedAccount = EmailRepository.saveAccount(account)
                    
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        binding.btnLogin.isEnabled = true
                        
                        Toast.makeText(this@LoginActivity, "登录成功！", Toast.LENGTH_SHORT).show()
                        
                        // 启动主界面
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                }
                is LoginResult.Error -> {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        binding.btnLogin.isEnabled = true
                        Toast.makeText(this@LoginActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
                is LoginResult.NeedsAuthorization -> {
                    // 需要OAuth2授权，启动浏览器进行登录
                    withContext(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        binding.btnLogin.isEnabled = true
                        
                        // 启动OAuth2授权流程
                        viewModel.startOAuth2Auth(email, password, displayName)
                        
                        Toast.makeText(this@LoginActivity, "正在跳转到浏览器进行安全登录...", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    /**
     * 观察登录状态
     */
    private fun observeLoginState() {
        lifecycleScope.launch {
            viewModel.loginState.collectLatest { state ->
                when (state) {
                    is LoginViewModel.LoginState.OAuth2Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnLogin.isEnabled = true
                        Toast.makeText(this@LoginActivity, "OAuth2授权成功！", Toast.LENGTH_SHORT).show()
                        
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                    is LoginViewModel.LoginState.OAuth2Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnLogin.isEnabled = true
                        Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    is LoginViewModel.LoginState.AutoLoginSuccess -> {
                        Toast.makeText(this@LoginActivity, "已自动登录！", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                    is LoginViewModel.LoginState.AutoLoginNoAccounts -> {
                        // 没有已保存的账户，显示登录界面
                    }
                }
            }
        }
    }
}
