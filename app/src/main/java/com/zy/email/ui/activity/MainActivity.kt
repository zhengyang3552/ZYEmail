package com.zy.email.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.zy.email.R
import com.zy.email.data.model.Account
import com.zy.email.data.repository.EmailMessage
import com.zy.email.databinding.ActivityMainBinding
import com.zy.email.ui.adapter.AccountListAdapter
import com.zy.email.ui.adapter.MessageListAdapter
import com.zy.email.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
        accountListAdapter = AccountListAdapter { account ->
            selectAccount(account)
        }
        
        binding.rvAccounts.layoutManager = LinearLayoutManager(this)
        binding.rvAccounts.adapter = accountListAdapter
        
        binding.swipeRefresh.setOnRefreshListener {
            refreshData()
        }
        
        messageListAdapter = MessageListAdapter { message ->
            showMessageDetail(message)
        }
        
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter = messageListAdapter
        binding.rvMessages.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        
        binding.fabCompose.setOnClickListener {
            if (currentAccountId > 0 && isShowingMessages) {
                openCompose()
            } else {
                Toast.makeText(this, "请先选择账户", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.toolbar.setNavigationOnClickListener {
            if (isShowingMessages) {
                showAccountList()
            } else {
                finish()
            }
        }
    }
    
    private fun loadAccounts() {
        lifecycleScope.launch {
            viewModel.accounts.collectLatest { accounts ->
                accountListAdapter.submitList(accounts)
            }
        }
    }
    
    private fun selectAccount(account: Account) {
        currentAccountId = account.id
        isShowingMessages = false
        
        binding.toolbar.title = account.email
        binding.toolbar.navigationIcon = null
        binding.rvAccounts.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.rvMessages.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        
        loadMessages(account.id)
    }
    
    private fun loadMessages(accountId: Long) {
        lifecycleScope.launch {
            viewModel.messages.collectLatest { messages ->
                messageListAdapter.submitList(messages)
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                
                if (messages.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "暂无邮件"
                } else {
                    binding.tvEmpty.visibility = View.GONE
                }
            }
        }
        
        viewModel.loadMessages(accountId)
    }
    
    private fun showAccountList() {
        isShowingMessages = false
        binding.toolbar.title = "ZYEmail"
        binding.toolbar.navigationIcon = null
        binding.rvAccounts.visibility = View.VISIBLE
        binding.rvMessages.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
    }
    
    private fun openCompose() {
        val intent = Intent(this, ComposeActivity::class.java)
        intent.putExtra("account_id", currentAccountId)
        startActivity(intent)
    }
    
    private fun showMessageDetail(message: EmailMessage) {
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
