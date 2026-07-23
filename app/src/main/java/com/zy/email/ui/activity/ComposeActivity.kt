package com.zy.email.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.zy.email.data.model.Account
import com.zy.email.data.repository.RoomDatabaseManager
import com.zy.email.data.repository.SendResult
import com.zy.email.data.repository.SmtpMailSender
import com.zy.email.databinding.ActivityComposeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 写信界面
 */
class ComposeActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityComposeBinding
    private var currentAccountId: Long = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComposeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        currentAccountId = intent.getLongExtra("account_id", 0)
        
        setupViews()
    }
    
    private fun setupViews() {
        // 抄送
        binding.tvCc.setOnClickListener {
            if (binding.llCc.visibility == View.GONE) {
                binding.llCc.visibility = View.VISIBLE
            } else {
                binding.llCc.visibility = View.GONE
            }
        }
        
        // 密送
        binding.tvBcc.setOnClickListener {
            if (binding.llBcc.visibility == View.GONE) {
                binding.llBcc.visibility = View.VISIBLE
            } else {
                binding.llBcc.visibility = View.GONE
            }
        }
        
        // 发送按钮
        binding.btnSend.setOnClickListener {
            sendEmail()
        }
        
        // 取消按钮
        binding.btnCancel.setOnClickListener {
            finish()
        }
        
        // 工具栏返回
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    /**
     * 发送邮件
     */
    private fun sendEmail() {
        val to = binding.editTo.text.toString().trim()
        val cc = if (binding.llCc.visibility == View.VISIBLE) {
            binding.editCc.text.toString().trim()
        } else null
        val bcc = if (binding.llBcc.visibility == View.VISIBLE) {
            binding.editBcc.text.toString().trim()
        } else null
        val subject = binding.editSubject.text.toString().trim()
        val body = binding.editBody.text.toString()
        
        if (to.isEmpty()) {
            binding.editTo.error = "请输入收件人"
            return
        }
        if (subject.isEmpty()) {
            binding.editSubject.error = "请输入主题"
            return
        }
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSend.isEnabled = false
        
        lifecycleScope.launch {
            val account = withContext(Dispatchers.IO) {
                RoomDatabaseManager.getAccountById(currentAccountId) as Account?
            }
            
            if (account != null) {
                val result = if (cc != null || bcc != null) {
                    SmtpMailSender.sendEmailWithCc(
                        account = account,
                        to = to,
                        cc = cc,
                        bcc = bcc,
                        subject = subject,
                        body = body
                    )
                } else {
                    SmtpMailSender.sendEmail(
                        account = account,
                        to = to,
                        subject = subject,
                        body = body
                    )
                }
                
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSend.isEnabled = true
                    
                    when (result) {
                        is SendResult.Success -> {
                            Toast.makeText(this@ComposeActivity, "发送成功！", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                        is SendResult.Error -> {
                            Toast.makeText(this@ComposeActivity, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnSend.isEnabled = true
                    Toast.makeText(this@ComposeActivity, "账户信息错误", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        // 草稿保存
        val subject = binding.editSubject.text.toString()
        val body = binding.editBody.text.toString()
        if (subject.isNotEmpty() || body.isNotEmpty()) {
            // 可以保存到草稿箱
        }
        finish()
    }
}
