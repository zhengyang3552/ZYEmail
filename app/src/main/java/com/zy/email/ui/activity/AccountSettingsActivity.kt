package com.zy.email.ui.activity

import android.content.DialogInterface
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.zy.email.data.repository.RoomDatabaseManager
import com.zy.email.data.model.Account
import kotlinx.coroutines.launch

/**
 * 账户设置界面
 */
class AccountSettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_settings)
        
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
        
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "账户设置"
        }
    }
    
    class SettingsFragment : PreferenceFragmentCompat() {
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            
            // 动态加载账户列表
            loadAccountList()
        }
        
        private fun loadAccountList() {
            lifecycleScope.launch {
                RoomDatabaseManager.getAllAccounts().collect { accounts ->
                    // 找到"账户列表"的PreferenceGroup
                    val accountGroup = findPreference<Preference>("account_list")
                    
                    if (accountGroup is androidx.preference.PreferenceGroup) {
                        // 清除现有项目
                        accountGroup.removeAll()
                        
                        // 添加账户项目
                        for (account in accounts) {
                            val pref = Preference(requireContext()).apply {
                                key = "account_${account.id}"
                                title = account.email
                                summary = if (account.accountType.name == "OAUTH2") "Microsoft/Google (OAuth2)" else "SMTP (${account.smtpServer})"
                                setOnPreferenceClickListener {
                                    showAccountOptions(account)
                                    true
                                }
                            }
                            accountGroup.addPreference(pref)
                        }
                        
                        // 添加"添加账户"按钮
                        val addAccount = Preference(requireContext()).apply {
                            key = "add_account"
                            title = "添加新账户"
                            setOnPreferenceClickListener {
                                // 返回登录界面
                                requireActivity().finish()
                                true
                            }
                        }
                        accountGroup.addPreference(addAccount)
                    }
                }
            }
        }
        
        private fun showAccountOptions(account: Account) {
            val options = arrayOf("编辑账户", "删除账户")
            
            AlertDialog.Builder(requireContext())
                .setTitle(account.email)
                .setItems(options) { dialog: DialogInterface, which: Int ->
                    when (which) {
                        0 -> {
                            // TODO: 编辑账户
                            Toast.makeText(requireContext(), "编辑功能开发中", Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            // 删除账户
                            AlertDialog.Builder(requireContext())
                                .setTitle("确认删除")
                                .setMessage("确定要删除账户 ${account.email} 吗？")
                                .setPositiveButton("删除") { _, _ ->
                                    lifecycleScope.launch {
                                        RoomDatabaseManager.deleteAccount(account)
                                        Toast.makeText(requireContext(), "账户已删除", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .setNegativeButton("取消", null)
                                .show()
                        }
                    }
                }
                .show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
