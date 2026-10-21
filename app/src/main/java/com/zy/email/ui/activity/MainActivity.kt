package com.zy.email.ui.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.zy.email.R
import com.zy.email.data.model.Account
import com.zy.email.data.repository.EmailMessage
import com.zy.email.data.repository.MailFolder
import com.zy.email.ui.theme.ThemeManager
import com.zy.email.databinding.ActivityMainBinding
import com.zy.email.ui.adapter.AccountListAdapter
import com.zy.email.ui.adapter.MessageListAdapter
import com.zy.email.ui.setVisible
import com.zy.email.ui.showSuccess
import com.zy.email.ui.tintMenuIcons
import com.zy.email.ui.viewmodel.MailUiState
import com.zy.email.ui.viewmodel.MainViewModel
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

/**
 * 主界面：账户列表 ⇄ 邮件列表。
 *
 * 与原实现的主要差异：
 *  · 状态驱动渲染：界面完全由 [MailUiState] 决定，空/错误/加载三态不再混淆
 *  · 空状态可操作：账户为空时引导添加账户，邮件为空或失败时提供刷新入口
 *  · 返回行为统一：邮件列表回账户列表，账户列表才退出应用
 *  · 配置变更（旋转/切换深浅色）后由 ViewModel 恢复当前账户，不丢失上下文
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var accountAdapter: AccountListAdapter
    private lateinit var messageAdapter: MessageListAdapter

    private var accounts: List<Account> = emptyList()
    private var showingMessages = false

    /** 程序化同步标签选中态时置 true，避免 onTabSelected 重复触发加载 */
    private var suppressTabCallback = false

    /** 写信返回后若发送成功（RESULT_OK），刷新当前收件箱 */
    private val composeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            binding.root.showSuccess(getString(R.string.compose_send_success))
            if (showingMessages) viewModel.refresh()
        }
    }

    /** 添加账户返回后给出明确反馈；账户列表本身由 Room 的 Flow 自动刷新 */
    private val addAccountLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            binding.root.showSuccess(getString(R.string.login_success))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 动态取色需在视图创建前应用（Android 12+ 且用户开启时生效）
        ThemeManager.applyDynamicColorIfEnabled(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        binding.toolbar.tintMenuIcons(R.color.m3_on_surface_variant)
        binding.toolbar.setNavigationOnClickListener { handleBack() }
        binding.toolbar.setOnMenuItemClickListener { onMenuItemSelected(it) }

        // 文件夹切换：收件箱 ⇄ 已发送
        binding.tabFolders.addTab(
            binding.tabFolders.newTab().setText(R.string.main_tab_inbox)
        )
        binding.tabFolders.addTab(
            binding.tabFolders.newTab().setText(R.string.main_tab_sent)
        )
        binding.tabFolders.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (suppressTabCallback) return
                val folder = if (tab.position == 0) MailFolder.INBOX else MailFolder.SENT
                viewModel.switchFolder(folder)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        accountAdapter = AccountListAdapter { account -> viewModel.openAccount(account) }
        binding.rvAccounts.layoutManager = LinearLayoutManager(this)
        binding.rvAccounts.adapter = accountAdapter

        messageAdapter = MessageListAdapter { message -> openMessageDetail(message) }
        binding.rvMessages.layoutManager = LinearLayoutManager(this)
        binding.rvMessages.adapter = messageAdapter

        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.m3_primary)
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.m3_surface_container_high)
        )
        binding.swipeRefresh.isEnabled = false
        binding.swipeRefresh.setOnRefreshListener {
            if (showingMessages) {
                viewModel.refresh()
            } else {
                binding.swipeRefresh.isRefreshing = false
            }
        }

        binding.fabCompose.setOnClickListener { openCompose() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.accounts.collect { list ->
                // null = 首次查询尚未返回（仅冷启动短暂出现）：只显示加载占位，
                // 不渲染列表也不渲染空账户引导页，消除"空状态闪现后跳实际内容"的闪烁
                if (list == null) {
                    if (!showingMessages) renderAccountsLoading()
                    return@collect
                }

                accounts = list
                accountAdapter.submitList(list)

                if (!showingMessages) {
                    renderAccounts(list)
                } else if (list.none { it.id == viewModel.currentAccountId }) {
                    // 当前账户已被删除（例如在设置页删除），回到账户列表
                    viewModel.clearSelection()
                } else {
                    updateMessagesHeader()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.mailState.collect { state -> renderMailState(state) }
        }
    }

    /** 账户首次查询前的占位：不展示任何引导/空态，仅显示标题与进度条 */
    private fun renderAccountsLoading() {
        binding.toolbar.setTitle(R.string.main_accounts_title)
        binding.toolbar.subtitle = getString(R.string.main_loading)
        binding.toolbar.setNavigationIcon(null)
        binding.toolbar.menu.findItem(R.id.action_refresh)?.isVisible = false
        binding.tabFolders.setVisible(false)

        binding.rvAccounts.setVisible(true)
        binding.rvMessages.setVisible(false)
        binding.fabCompose.setVisible(false)
        binding.emptyState.setVisible(false)
        binding.progressBar.setVisible(true)
    }

    // ==================== 渲染 ====================

    private fun renderAccounts(list: List<Account>) {
        showingMessages = false
        binding.toolbar.setTitle(R.string.main_accounts_title)
        binding.toolbar.subtitle = getString(R.string.main_accounts_subtitle, list.size)
        binding.toolbar.setNavigationIcon(null)
        binding.toolbar.menu.findItem(R.id.action_refresh)?.isVisible = false
        binding.tabFolders.setVisible(false)

        binding.rvAccounts.setVisible(true)
        binding.rvMessages.setVisible(false)
        binding.fabCompose.setVisible(false)
        binding.swipeRefresh.isEnabled = false
        binding.swipeRefresh.isRefreshing = false
        binding.progressBar.setVisible(false)

        if (list.isEmpty()) {
            showStatusPanel(
                iconRes = R.drawable.ic_inbox,
                tintRes = R.color.m3_primary,
                title = getString(R.string.main_empty_accounts_title),
                description = getString(R.string.main_empty_accounts_desc),
                actionText = getString(R.string.main_empty_action_add)
            ) { openLoginForResult() }
        } else {
            binding.emptyState.setVisible(false)
        }
    }

    private fun renderMailState(state: MailUiState) {
        when (state) {
            // Idle 且回到账户列表：仅当账户查询已有结果时才渲染，
            // 否则保持加载占位（避免启动时空账户引导页闪现）
            MailUiState.Idle -> if (viewModel.accounts.value != null) renderAccounts(accounts)

            MailUiState.Loading -> {
                showMessagesLayout()
                // 统一用顶部线性进度条表达加载中，避免与下拉刷新指示器叠加
                binding.swipeRefresh.isRefreshing = false
                binding.progressBar.setVisible(true)
                binding.toolbar.subtitle = getString(R.string.main_loading)
                binding.emptyState.setVisible(false)
            }

            is MailUiState.Content -> {
                showMessagesLayout()
                binding.progressBar.setVisible(false)
                binding.emptyState.setVisible(false)
                messageAdapter.setSentMode(viewModel.currentFolder == MailFolder.SENT)
                messageAdapter.submitList(state.messages)
                binding.toolbar.subtitle =
                    getString(R.string.main_messages_subtitle, state.messages.size)
            }

            MailUiState.Empty -> {
                showMessagesLayout()
                binding.progressBar.setVisible(false)
                messageAdapter.submitList(emptyList())
                val sentMode = viewModel.currentFolder == MailFolder.SENT
                showStatusPanel(
                    iconRes = if (sentMode) R.drawable.ic_send else R.drawable.ic_inbox,
                    tintRes = R.color.m3_primary,
                    title = getString(
                        if (sentMode) R.string.main_empty_sent_title
                        else R.string.main_empty_messages_title
                    ),
                    description = getString(
                        if (sentMode) R.string.main_empty_sent_desc
                        else R.string.main_empty_messages_desc
                    ),
                    actionText = getString(R.string.main_empty_action_refresh)
                ) { viewModel.refresh() }
            }

            is MailUiState.Error -> {
                showMessagesLayout()
                binding.progressBar.setVisible(false)
                messageAdapter.submitList(emptyList())
                showStatusPanel(
                    iconRes = R.drawable.ic_warning,
                    tintRes = R.color.m3_error,
                    title = getString(R.string.main_error_title),
                    description = state.message,
                    actionText = getString(R.string.action_retry)
                ) { viewModel.refresh() }
            }
        }
    }

    private fun showMessagesLayout() {
        showingMessages = true
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.menu.findItem(R.id.action_refresh)?.isVisible = true
        binding.rvAccounts.setVisible(false)
        binding.rvMessages.setVisible(true)
        binding.fabCompose.setVisible(true)
        binding.swipeRefresh.isEnabled = true

        // 同步文件夹标签选中态；clearSelection 后回到收件箱
        val targetIndex = if (viewModel.currentFolder == MailFolder.SENT) 1 else 0
        suppressTabCallback = true
        binding.tabFolders.setVisible(true)
        binding.tabFolders.getTabAt(targetIndex)?.select()
        suppressTabCallback = false

        updateMessagesHeader()
    }

    private fun updateMessagesHeader() {
        val account = accounts.firstOrNull { it.id == viewModel.currentAccountId }
        binding.toolbar.title = account?.email ?: getString(R.string.main_title)
    }

    private fun showStatusPanel(
        @DrawableRes iconRes: Int,
        @ColorRes tintRes: Int,
        title: String,
        description: String,
        actionText: String?,
        onAction: (() -> Unit)?
    ) {
        binding.emptyState.setVisible(true)
        binding.ivEmptyIcon.setImageResource(iconRes)
        binding.ivEmptyIcon.imageTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, tintRes))
        binding.tvEmptyTitle.text = title
        binding.tvEmptySubtitle.text = description

        if (actionText != null && onAction != null) {
            binding.btnEmptyAction.setVisible(true)
            binding.btnEmptyAction.text = actionText
            binding.btnEmptyAction.setOnClickListener { onAction() }
        } else {
            binding.btnEmptyAction.setVisible(false)
        }
    }

    // ==================== 交互 ====================

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                if (showingMessages) viewModel.refresh()
                true
            }

            R.id.action_add_account -> {
                openLoginForResult()
                true
            }

            R.id.action_settings -> {
                startActivity(Intent(this, AccountSettingsActivity::class.java))
                true
            }

            else -> false
        }
    }

    private fun handleBack() {
        if (showingMessages) {
            viewModel.clearSelection()
        } else {
            finish()
        }
    }

    private fun openCompose() {
        val accountId = viewModel.currentAccountId
        if (accountId <= 0L) {
            Snackbar.make(
                binding.root,
                getString(R.string.main_select_account_first),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val intent = Intent(this, ComposeActivity::class.java)
            .putExtra(ComposeActivity.EXTRA_ACCOUNT_ID, accountId)
        composeLauncher.launch(intent)
    }

    private fun openMessageDetail(message: EmailMessage) {
        // 打开即视为已读：乐观更新未读点 + 异步发送 IMAP \Seen 标记
        if (!message.isRead) {
            viewModel.markRead(message.uid)
        }

        val isSent = viewModel.currentFolder == MailFolder.SENT
        val intent = Intent(this, MessageDetailActivity::class.java).apply {
            putExtra(MessageDetailActivity.EXTRA_ACCOUNT_ID, viewModel.currentAccountId)
            putExtra(MessageDetailActivity.EXTRA_IS_SENT, isSent)
            putExtra(MessageDetailActivity.EXTRA_SUBJECT, message.subject)
            putExtra(MessageDetailActivity.EXTRA_FROM, message.from)
            putExtra(MessageDetailActivity.EXTRA_TO, message.to)
            putExtra(MessageDetailActivity.EXTRA_CC, message.cc)
            putExtra(MessageDetailActivity.EXTRA_DATE, message.date?.time ?: 0L)
            putExtra(MessageDetailActivity.EXTRA_BODY_HTML, message.bodyHtml)
            putExtra(MessageDetailActivity.EXTRA_BODY_TEXT, message.bodyText)
            putExtra(MessageDetailActivity.EXTRA_HAS_ATTACHMENTS, message.hasAttachments)
        }
        startActivity(intent)
    }

    private fun openLoginForResult() {
        val intent = Intent(this, LoginActivity::class.java)
            .putExtra(LoginActivity.EXTRA_ADD_MODE, true)
        addAccountLauncher.launch(intent)
    }
}
