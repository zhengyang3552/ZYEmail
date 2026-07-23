package com.zy.email.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.zy.email.R
import com.zy.email.databinding.ActivityMainBinding
import com.zy.email.ui.adapter.AccountListAdapter
import com.zy.email.ui.adapter.MessageListAdapter
import com.zy.email.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 主界面
 * 显示账户列表，点击账户进入邮件列表
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var accountListAdapter: AccountListAdapter
    private lateinit var messageListAdapter: MessageListAdapter
    
    private var currentAccountId: Long = 0
    private var isShowingMessages = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
        loadAccounts()
    }
    
    private fun setupViews() {
        // 账户列表
        accountListAdapter = AccountListAdapter { account ->
            selectAccount(account)
        }
        
        binding.rvAccounts.layoutManager = LinearLayoutManager(this)
        binding.rvAccounts.adapter = accountListAdapter
        
        // 下拉刷新
        binding.swipeRefresh.setOnRefreshListener {
            refreshData()
        }
        
        // 邮件列表（隐藏状态）
        messageListAdapter = MessageListAdapter { message ->
            showMessageDetail(message)
        }
        
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter = messageListAdapter
        binding.rvMessages.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        
        // 浮动操作按钮 - 写信
        binding.fabCompose.setOnClickListener {
            if (currentAccountId > 0 && isShowingMessages) {
                openCompose()
            } else {
                Toast.makeText(this, "请先选择账户", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 返回按钮
        binding.toolbar.setNavigationOnClickListener {
            if (isShowingMessages) {
                showAccountList()
            } else {
                finish()
            }
        }
    }
    
    /**
     * 加载账户列表
     */
    private fun loadAccounts() {
        lifecycleScope.launch {
            viewModel.accounts.collectLatest { accounts ->
                accountListAdapter.submitList(accounts)
            }
        }
    }
    
    /**
     * 选择账户并加载邮件
     */
    private fun selectAccount(account: com.zy.email.data.model.Account) {
        currentAccountId = account.id
        isShowingMessages = false
        
        // 显示邮件列表
        binding.toolbar.title = account.email
        binding.toolbar.navigationIcon = null
        binding.rvAccounts.visibility = android.view.View.GONE
        binding.tvEmpty.visibility = android.view.View.GONE
        binding.rvMessages.visibility = android.view.View.VISIBLE
        binding.progressBar.visibility = android.view.View.VISIBLE
        
        // 加载邮件
        loadMessages(account.id)
    }
    
    /**
     * 加载邮件列表
     */
    private fun loadMessages(accountId: Long) {
        lifecycleScope.launch {
            viewModel.messages.collectLatest { messages ->
                messageListAdapter.submitList(messages)
                binding.progressBar.visibility = android.view.View.GONE
                binding.swipeRefresh.isRefreshing = false
                
                if (messages.isEmpty()) {
                    binding.tvEmpty.visibility = android.view.View.VISIBLE
                    binding.tvEmpty.text = "暂无邮件"
                } else {
                    binding.tvEmpty.visibility = android.view.View.GONE
                }
            }
        }
        
        viewModel.loadMessages(accountId)
    }
    
    /**
     * 显示账户列表
     */
    private fun showAccountList() {
        isShowingMessages = false
        binding.tvCurrentAccount.text = "邮箱"
        binding.toolbar.title = "ZYEmail"
        binding.toolbar.navigationIcon = null
        binding.rvAccounts.visibility = android.view.View.VISIBLE
        binding.rvMessages.visibility = android.view.View.GONE
        binding.tvEmpty.visibility = android.view.View.GONE
    }
    
    /**
     * 打开写信界面
     */
    private fun openCompose() {
        val intent = Intent(this, ComposeActivity::class.java)
        intent.putExtra("account_id", currentAccountId)
        startActivity(intent)
    }
    
    /**
     * 显示邮件详情
     */
    private fun showMessageDetail(message: com.zy.email.data.repository.EmailMessage) {
        val intent = Intent(this, MessageDetailActivity::class.java)
        intent.putExtra("subject", message.subject)
        intent.putExtra("from", message.from)
        intent.putExtra("to", message.to)
        intent.putExtra("date", message.date?.time ?: 0)
        intent.putExtra("bodyHtml", message.bodyHtml)
        intent.putExtra("bodyText", message.bodyText)
        intent.putExtra("hasAttachments", message.hasAttachments)
        intent.putExtra("messageId", message.messageId)
        startActivity(intent)
    }
    
    /**
     * 刷新数据
     */
    private fun refreshData() {
        if (currentAccountId > 0) {
            viewModel.loadMessages(currentAccountId)
        } else {
            viewModel.loadAccounts()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_account -> {
                startActivity(Intent(this, LoginActivity::class.java))
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, AccountSettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.loadAccounts()
    }
}
