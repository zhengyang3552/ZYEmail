package com.zy.email.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zy.email.R
import com.zy.email.data.model.Account
import com.zy.email.data.model.AccountType
import com.zy.email.data.repository.EmailRepository
import com.zy.email.databinding.ActivityAccountSettingsBinding
import com.zy.email.databinding.ItemAccountSettingBinding
import com.zy.email.ui.setVisible
import com.zy.email.ui.showSuccess
import com.zy.email.ui.theme.ThemeManager
import com.zy.email.utils.ErrorMessages
import kotlinx.coroutines.launch

/**
 * 设置页。
 *
 * 原实现基于 PreferenceFragmentCompat，但主题为 NoActionBar，
 * `supportActionBar` 为 null，导致页面既没有标题也没有返回按钮；
 * 同时"编辑账户/添加账户"两个入口要么是占位提示、要么直接关掉页面。
 *
 * 这里改为原生布局 + MaterialToolbar，并把三个真正有用的能力补齐：
 *  · 账户列表与删除（带二次确认与失败提示）
 *  · 外观模式切换（跟随系统 / 浅色 / 深色）
 *  · 版本信息
 */
class AccountSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountSettingsBinding
    private var accounts: List<Account> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        // 动态取色需在视图创建前应用（Android 12+ 且用户开启时生效）
        ThemeManager.applyDynamicColorIfEnabled(this)
        super.onCreate(savedInstanceState)
        binding = ActivityAccountSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvVersion.text = getString(R.string.settings_about_version, versionName())

        binding.btnAddAccount.setOnClickListener {
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .putExtra(LoginActivity.EXTRA_ADD_MODE, true)
            )
        }

        setupThemeSelector()
        observeAccounts()
    }

    /** 读取版本号；不使用 BuildConfig，避免受 buildConfig 生成开关影响 */
    private fun versionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun setupThemeSelector() {
        // 初始化选中项
        when (ThemeManager.currentMode(this)) {
            ThemeManager.MODE_LIGHT -> binding.rbThemeLight.isChecked = true
            ThemeManager.MODE_DARK -> binding.rbThemeDark.isChecked = true
            else -> binding.rbThemeSystem.isChecked = true
        }

        binding.rowThemeSystem.setOnClickListener { applyThemeMode(ThemeManager.MODE_SYSTEM) }
        binding.rowThemeLight.setOnClickListener { applyThemeMode(ThemeManager.MODE_LIGHT) }
        binding.rowThemeDark.setOnClickListener { applyThemeMode(ThemeManager.MODE_DARK) }

        // 动态取色开关：保存偏好后重建界面立即生效（Android 12 以下无视觉效果）
        binding.swDynamicColor.isChecked = ThemeManager.dynamicColorEnabled(this)
        binding.swDynamicColor.setOnCheckedChangeListener { _, checked ->
            ThemeManager.setDynamicColorEnabled(this, checked)
            recreate()
        }
    }

    /**
     * 切换外观。AppCompatDelegate 会重建当前 Activity，
     * 重建后 onCreate 会重新读取偏好并勾选对应的单选项。
     */
    private fun applyThemeMode(mode: Int) {
        if (mode == ThemeManager.currentMode(this)) return
        ThemeManager.setMode(this, mode)
    }

    private fun observeAccounts() {
        lifecycleScope.launch {
            EmailRepository.getAllAccounts().collect { list ->
                accounts = list
                renderAccounts(list)
            }
        }
    }

    private fun renderAccounts(list: List<Account>) {
        binding.accountListContainer.removeAllViews()
        binding.tvEmptyAccounts.setVisible(list.isEmpty())

        list.forEach { account ->
            val itemBinding = ItemAccountSettingBinding.inflate(
                layoutInflater,
                binding.accountListContainer,
                false
            )
            itemBinding.tvEmail.text = account.email
            itemBinding.tvType.text = when (account.accountType) {
                AccountType.SMTP -> getString(
                    R.string.settings_account_type_smtp,
                    account.imapServer.ifBlank { account.smtpServer }
                )
                AccountType.OAUTH2 -> getString(R.string.settings_account_type_oauth2)
            }
            itemBinding.btnDelete.setOnClickListener { confirmDelete(account) }

            binding.accountListContainer.addView(itemBinding.root)
        }
    }

    private fun confirmDelete(account: Account) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_delete_title)
            .setMessage(getString(R.string.settings_delete_message, account.email))
            .setPositiveButton(R.string.action_delete) { _, _ -> deleteAccount(account) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun deleteAccount(account: Account) {
        lifecycleScope.launch {
            try {
                EmailRepository.deleteAccount(account)
                binding.root.showSuccess(getString(R.string.settings_account_deleted))
            } catch (e: Exception) {
                showMessage(
                    getString(
                        R.string.settings_account_delete_failed,
                        ErrorMessages.from(this@AccountSettingsActivity, e)
                    )
                )
            }
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
}
