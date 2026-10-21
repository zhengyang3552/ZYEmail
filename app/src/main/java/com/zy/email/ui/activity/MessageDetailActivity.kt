package com.zy.email.ui.activity

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zy.email.R
import com.zy.email.databinding.ActivityMessageDetailBinding
import com.zy.email.ui.setVisible
import com.zy.email.ui.theme.ThemeManager
import com.zy.email.utils.DateFormatUtils
import com.zy.email.utils.EmailUtils
import java.util.Date

/**
 * 邮件详情页。
 *
 * 主要改动：
 *  · 回复 / 全部回复 / 转发从"功能开发中"的占位提示，改为真实跳转写信页并预填
 *    收件人、主题前缀与引用原文。
 *  · HTML 与纯文本互斥渲染。原实现无论是否有 HTML 都把 WebView 设为可见，
 *    纯文本邮件会出现"空白页 + 下方纯文本"的错位。
 *  · 注入与主题一致的排版样式：深色模式下邮件正文不再是一块刺眼的白色。
 *  · WebView 关闭 JavaScript / 文件访问，仅保留图片加载，降低邮件 HTML 的攻击面。
 */
class MessageDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT_ID = "extra_account_id"
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_FROM = "extra_from"
        const val EXTRA_TO = "extra_to"
        const val EXTRA_CC = "extra_cc"
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_BODY_HTML = "extra_body_html"
        const val EXTRA_BODY_TEXT = "extra_body_text"
        const val EXTRA_HAS_ATTACHMENTS = "extra_has_attachments"
    }

    private lateinit var binding: ActivityMessageDetailBinding

    private var accountId = 0L
    private var subject = ""
    private var from = ""
    private var to = ""
    private var cc = ""
    private var dateMillis = 0L
    private var bodyHtml = ""
    private var bodyText = ""

    /** 收件人信息是否处于展开状态（默认单行省略） */
    private var recipientExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 动态取色需在视图创建前应用（Android 12+ 且用户开启时生效）
        ThemeManager.applyDynamicColorIfEnabled(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMessageDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        readExtras()
        setupToolbar()
        bindHeader()
        bindBody()
        bindActions()
    }

    private fun readExtras() {
        accountId = intent.getLongExtra(EXTRA_ACCOUNT_ID, 0L)
        subject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty()
        from = intent.getStringExtra(EXTRA_FROM).orEmpty()
        to = intent.getStringExtra(EXTRA_TO).orEmpty()
        cc = intent.getStringExtra(EXTRA_CC).orEmpty()
        dateMillis = intent.getLongExtra(EXTRA_DATE, 0L)
        bodyHtml = intent.getStringExtra(EXTRA_BODY_HTML).orEmpty()
        bodyText = intent.getStringExtra(EXTRA_BODY_TEXT).orEmpty()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        if (intent.getBooleanExtra(EXTRA_HAS_ATTACHMENTS, false)) {
            binding.toolbar.subtitle = getString(R.string.message_has_attachment)
        }
    }

    private fun bindHeader() {
        binding.tvSubject.text = subject.ifBlank { getString(R.string.detail_no_subject) }

        binding.tvFrom.text = EmailUtils.displayNameOf(from)
            .ifBlank { getString(R.string.message_unknown_sender) }
        binding.tvAvatar.text = EmailUtils.initialOf(from)

        binding.tvTo.text = buildRecipientsText()
        binding.tvTo.setOnClickListener {
            recipientExpanded = !recipientExpanded
            binding.tvTo.maxLines = if (recipientExpanded) 6 else 1
        }

        binding.tvDate.text = if (dateMillis > 0L) {
            DateFormatUtils.forDetail(Date(dateMillis))
        } else {
            getString(R.string.detail_date_unknown)
        }
    }

    private fun buildRecipientsText(): String {
        val toText = if (to.isBlank()) "—" else to
        val base = getString(R.string.detail_to_format, toText)
        return if (cc.isBlank()) base else "$base\n${getString(R.string.detail_cc_format, cc)}"
    }

    // ==================== 正文 ====================

    private fun bindBody() {
        when {
            bodyHtml.isNotBlank() -> renderHtml(bodyHtml)
            bodyText.isNotBlank() -> renderPlainText(bodyText)
            else -> renderPlainText(getString(R.string.error_empty_message))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun renderHtml(html: String) {
        binding.svPlain.setVisible(false)
        binding.wvBody.setVisible(true)
        binding.tvPlain.setVisible(false)

        binding.wvBody.setBackgroundColor(ContextCompat.getColor(this, R.color.m3_surface))

        val settings = binding.wvBody.settings
        // 邮件 HTML 不需要脚本；关闭后同时禁用本地文件与内容访问，收敛攻击面
        settings.javaScriptEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false

        binding.wvBody.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString().orEmpty()
                openExternally(url)
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                // 主文档加载失败时降级为纯文本，并明确告知用户
                if (request?.isForMainFrame == true) {
                    view?.setVisible(false)
                    renderPlainText(
                        bodyText.ifBlank { EmailUtils.htmlToPlainText(html) }
                    )
                    binding.tvPlain.setVisible(true)
                }
            }
        }

        binding.wvBody.loadDataWithBaseURL(null, wrapHtml(html), "text/html", "UTF-8", null)
    }

    private fun renderPlainText(text: String) {
        binding.wvBody.setVisible(false)
        binding.svPlain.setVisible(true)
        binding.tvBody.text = text
    }

    /**
     * 把邮件 HTML 包进带主题样式的文档壳：
     *  · 禁止横向溢出（邮件常见固定宽度表格）
     *  · 链接使用主色，引用块使用次级文字色
     *  · color-scheme 让浏览器内核同时调整滚动条等原生控件
     */
    private fun wrapHtml(content: String): String {
        val dark = ThemeManager.isNightMode(this)
        val textColor = themeColor(R.color.m3_on_surface)
        val backgroundColor = themeColor(R.color.m3_surface)
        val linkColor = themeColor(R.color.m3_primary)
        val mutedColor = themeColor(R.color.m3_on_surface_variant)

        return """
            <!DOCTYPE html>
            <html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              :root { color-scheme: ${if (dark) "dark" else "light"}; }
              html, body {
                margin: 0; padding: 16px;
                background: $backgroundColor; color: $textColor;
                font-family: -apple-system, "Noto Sans SC", Roboto, "Helvetica Neue", sans-serif;
                font-size: 16px; line-height: 1.6;
                word-wrap: break-word; overflow-wrap: break-word;
                -webkit-text-size-adjust: 100%;
              }
              img, video, table { max-width: 100% !important; height: auto; }
              a { color: $linkColor; }
              blockquote {
                margin: 8px 0; padding: 4px 12px;
                border-left: 3px solid $mutedColor; color: $mutedColor;
              }
              pre { white-space: pre-wrap; word-break: break-word; }
              * { max-width: 100% !important; }
            </style>
            </head><body>$content</body></html>
        """.trimIndent()
    }

    private fun themeColor(colorRes: Int): String {
        val color = ContextCompat.getColor(this, colorRes)
        return String.format("#%06X", 0xFFFFFF and color)
    }

    private fun openExternally(url: String) {
        if (url.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            // 没有可处理该链接的应用时静默忽略，避免崩溃
            binding.tvPlain.setVisible(true)
            binding.tvPlain.text = getString(R.string.detail_webview_failed)
            binding.tvPlain.setBackgroundColor(
                ContextCompat.getColor(this, R.color.m3_warning_container)
            )
        }
    }

    // ==================== 操作 ====================

    private fun bindActions() {
        binding.btnReply.setOnClickListener { openCompose(ComposeMode.REPLY) }
        binding.btnReplyAll.setOnClickListener { openCompose(ComposeMode.REPLY_ALL) }
        binding.btnForward.setOnClickListener { openCompose(ComposeMode.FORWARD) }
    }

    private fun openCompose(mode: ComposeMode) {
        val intent = Intent(this, ComposeActivity::class.java).apply {
            putExtra(ComposeActivity.EXTRA_ACCOUNT_ID, accountId)

            when (mode) {
                ComposeMode.REPLY, ComposeMode.REPLY_ALL -> {
                    putExtra(ComposeActivity.EXTRA_TO, EmailUtils.bareAddressOf(from))
                    if (mode == ComposeMode.REPLY_ALL) {
                        val senderAddress = EmailUtils.bareAddressOf(from)
                        val ccAddresses = to.split(",")
                            .map { it.trim() }
                            .filter {
                                it.isNotEmpty() &&
                                        !EmailUtils.bareAddressOf(it).equals(senderAddress, true)
                            }
                            .joinToString(", ")
                        putExtra(ComposeActivity.EXTRA_CC, ccAddresses)
                    }
                    putExtra(
                        ComposeActivity.EXTRA_SUBJECT,
                        getString(R.string.detail_reply_prefix, subject)
                    )
                    putExtra(ComposeActivity.EXTRA_BODY, buildQuote())
                }

                ComposeMode.FORWARD -> {
                    putExtra(
                        ComposeActivity.EXTRA_SUBJECT,
                        getString(R.string.detail_forward_prefix, subject)
                    )
                    putExtra(ComposeActivity.EXTRA_BODY, buildQuote())
                }
            }
        }
        startActivity(intent)
    }

    /** 引用原文：发件人 / 时间 / 主题 + 纯文本正文 */
    private fun buildQuote(): String {
        val builder = StringBuilder()
        builder.append("\n\n").append(getString(R.string.detail_quote_header)).append("\n")
        builder.append(getString(R.string.detail_from_label)).append(": ").append(from).append("\n")
        if (dateMillis > 0L) {
            builder.append(DateFormatUtils.forQuote(Date(dateMillis))).append("\n")
        }
        builder.append(subject.ifBlank { getString(R.string.detail_no_subject) }).append("\n\n")

        val quotedBody = bodyText.ifBlank { EmailUtils.htmlToPlainText(bodyHtml) }
        builder.append(quotedBody)
        return builder.toString()
    }

    private enum class ComposeMode { REPLY, REPLY_ALL, FORWARD }
}
