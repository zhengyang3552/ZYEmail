package com.zy.email.ui.activity

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.zy.email.R
import com.zy.email.data.model.Account
import com.zy.email.data.repository.EmailRepository
import com.zy.email.data.repository.SendResult
import com.zy.email.ui.theme.ThemeManager
import com.zy.email.databinding.ActivityComposeBinding
import com.zy.email.ui.setVisible
import com.zy.email.utils.EmailUtils
import com.zy.email.utils.ErrorMessages
import kotlinx.coroutines.launch
import javax.mail.internet.InternetAddress

/**
 * 写信页。
 *
 * 复用于新建、回复、全部回复、转发四种场景，通过 Intent extra 预填字段。
 *
 * 关键修正：
 *  · 抄送/密送开关原本被放在"被折叠的容器内部"，逻辑上永远无法展开；
 *    现改为容器外的 Chip 控制对应字段的显示。
 *  · 发送前逐个字段校验地址格式，错误定位到具体输入框，而不是统一报"发送失败"。
 *  · 发送期间禁用按钮并显示进度，防止重复投递。
 *  · 编辑过内容后返回会二次确认，避免误触丢失正文。
 */
class ComposeActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT_ID = "extra_account_id"
        const val EXTRA_TO = "extra_to"
        const val EXTRA_CC = "extra_cc"
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_BODY = "extra_body"
    }

    private lateinit var binding: ActivityComposeBinding

    private var currentAccountId = 0L
    private var account: Account? = null
    private var sending = false

    /** 进入页面时的编辑内容快照，用于判断是否产生未保存修改 */
    private var baseline: EditorState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 动态取色需在视图创建前应用（Android 12+ 且用户开启时生效）
        ThemeManager.applyDynamicColorIfEnabled(this)
        super.onCreate(savedInstanceState)
        binding = ActivityComposeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentAccountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, 0L)

        setupViews()
        prefillFromIntent()
        loadAccount()
    }

    private fun setupViews() {
        binding.toolbar.setNavigationOnClickListener { confirmDiscard() }

        binding.chipCc.setOnCheckedChangeListener { _, checked ->
            binding.tilCc.setVisible(checked)
        }
        binding.chipBcc.setOnCheckedChangeListener { _, checked ->
            binding.tilBcc.setVisible(checked)
        }

        binding.btnSend.setOnClickListener { send() }
        binding.btnCancel.setOnClickListener { confirmDiscard() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                confirmDiscard()
            }
        })
    }

    private fun prefillFromIntent() {
        intent.getStringExtra(EXTRA_TO)?.takeIf { it.isNotBlank() }?.let {
            binding.editTo.setText(it)
        }
        intent.getStringExtra(EXTRA_CC)?.takeIf { it.isNotBlank() }?.let {
            binding.editCc.setText(it)
            binding.chipCc.isChecked = true
            binding.tilCc.setVisible(true)
        }
        intent.getStringExtra(EXTRA_SUBJECT)?.let {
            binding.editSubject.setText(it)
        }
        intent.getStringExtra(EXTRA_BODY)?.takeIf { it.isNotBlank() }?.let {
            binding.editBody.setText(it)
        }

        // 预填完成后再记录基线，避免"回复"场景一进入就被判定为有未保存修改
        baseline = currentState()
    }

    private fun loadAccount() {
        lifecycleScope.launch {
            val loaded = if (currentAccountId > 0L) {
                EmailRepository.getAccount(currentAccountId)
            } else {
                EmailRepository.getFirstAccount()
            }

            if (loaded == null) {
                showMessage(getString(R.string.compose_error_account_missing))
                setResult(RESULT_CANCELED)
                finish()
                return@launch
            }

            account = loaded
            currentAccountId = loaded.id
            binding.tvFromAccount.text =
                getString(R.string.compose_from_account, loaded.email)
        }
    }

    // ==================== 发送 ====================

    private fun send() {
        if (sending) return

        binding.tilTo.error = null
        binding.tilCc.error = null
        binding.tilBcc.error = null

        val toRaw = binding.editTo.text?.toString()?.trim().orEmpty()
        val ccRaw = if (binding.tilCc.isVisible) {
            binding.editCc.text?.toString()?.trim().orEmpty()
        } else {
            ""
        }
        val bccRaw = if (binding.tilBcc.isVisible) {
            binding.editBcc.text?.toString()?.trim().orEmpty()
        } else {
            ""
        }
        val subject = binding.editSubject.text?.toString()?.trim().orEmpty()
        val body = binding.editBody.text?.toString().orEmpty()

        if (toRaw.isEmpty()) {
            binding.tilTo.error = getString(R.string.compose_error_to_required)
            binding.editTo.requestFocus()
            return
        }

        val to = parseOrReport(toRaw, binding.tilTo, R.string.compose_error_to_invalid) ?: return
        val cc = parseOrReport(ccRaw, binding.tilCc, R.string.compose_error_cc_invalid) ?: return
        val bcc = parseOrReport(bccRaw, binding.tilBcc, R.string.compose_error_bcc_invalid) ?: return

        val currentAccount = account
        if (currentAccount == null) {
            showMessage(getString(R.string.compose_error_account_missing))
            return
        }

        sending = true
        renderSendingState(true)

        lifecycleScope.launch {
            val result = EmailRepository.sendMail(
                account = currentAccount,
                to = to,
                cc = cc,
                bcc = bcc,
                subject = subject,
                body = body
            )

            sending = false
            renderSendingState(false)

            when (result) {
                SendResult.Success -> {
                    // 发送成功后不再询问"是否放弃"，直接结束
                    baseline = currentState()
                    setResult(RESULT_OK)
                    finish()
                }

                is SendResult.Error -> {
                    val message = result.throwable
                        ?.let { ErrorMessages.from(this@ComposeActivity, it) }
                        ?: result.rawMessage
                        ?: getString(R.string.error_generic)
                    showMessage(message)
                }
            }
        }
    }

    /** 解析地址列表；失败时把错误显示在对应输入框上并返回 null */
    private fun parseOrReport(
        raw: String,
        layout: com.google.android.material.textfield.TextInputLayout,
        errorRes: Int
    ): List<InternetAddress>? {
        if (raw.isEmpty()) return emptyList()
        return try {
            EmailUtils.parseRecipients(raw)
        } catch (e: IllegalArgumentException) {
            layout.error = getString(errorRes, e.message ?: raw)
            layout.requestFocus()
            null
        }
    }

    private fun renderSendingState(isSending: Boolean) {
        binding.progressBar.setVisible(isSending)
        binding.btnSend.isEnabled = !isSending
        binding.btnCancel.isEnabled = !isSending
        binding.btnSend.text = getString(
            if (isSending) R.string.compose_sending else R.string.compose_send
        )
    }

    // ==================== 退出确认 ====================

    private fun confirmDiscard() {
        if (sending) return
        if (!hasUnsavedChanges()) {
            finish()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.compose_discard_title)
            .setMessage(R.string.compose_discard_desc)
            .setPositiveButton(R.string.compose_discard_confirm) { _, _ -> finish() }
            .setNegativeButton(R.string.compose_discard_keep, null)
            .show()
    }

    private fun hasUnsavedChanges(): Boolean {
        val origin = baseline ?: return false
        return origin != currentState()
    }

    private fun currentState() = EditorState(
        to = binding.editTo.text?.toString()?.trim().orEmpty(),
        cc = binding.editCc.text?.toString()?.trim().orEmpty(),
        bcc = binding.editBcc.text?.toString()?.trim().orEmpty(),
        subject = binding.editSubject.text?.toString()?.trim().orEmpty(),
        body = binding.editBody.text?.toString().orEmpty()
    )

    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private data class EditorState(
        val to: String,
        val cc: String,
        val bcc: String,
        val subject: String,
        val body: String
    )
}
