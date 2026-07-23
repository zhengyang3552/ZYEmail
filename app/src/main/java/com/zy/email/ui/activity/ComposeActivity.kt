package com.zy.email.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.zy.email.R
import com.zy.email.data.model.Account
import com.zy.email.data.repository.RoomDatabaseManager
import com.zy.email.data.repository.SendResult
import com.zy.email.data.repository.SmtpMailSender
import com.zy.email.databinding.ActivityComposeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        binding.tvCc.setOnClickListener {
            if (binding.ccLayout.visibility == View.GONE) {
                binding.ccLayout.visibility = View.VISIBLE
            } else {
                binding.ccLayout.visibility = View.GONE
            }
        }
        
        binding.tvBcc.setOnClickListener {
            if (binding.bccLayout.visibility == View.GONE) {
                binding.bccLayout.visibility = View.VISIBLE
            } else {
                binding.bccLayout.visibility = View.GONE
            }
        }
        
        binding.btnSend.setOnClickListener { sendEmail() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
    
    private fun sendEmail() {
        val to = binding.editTo.text.toString().trim()
        val cc = if (binding.ccLayout.visibility == View.VISIBLE) binding.editCc.text.toString().trim() else null
        val bcc = if (binding.bccLayout.visibility == View.VISIBLE) binding.editBcc.text.toString().trim() else null
        val subject = binding.editSubject.text.toString().trim()
        val body = binding.editBody.text.toString()
        
        if (to.isEmpty()) { binding.editTo.error = "请输入收件人"; return }
        if (subject.isEmpty()) { binding.editSubject.error = "请输入主题"; return }
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSend.isEnabled = false
        
        lifecycleScope.launch {
            val account = withContext(Dispatchers.IO) {
                RoomDatabaseManager.getAccountById(currentAccountId) as Account?
            }
            
            if (account != null) {
                val result = if (cc != null || bcc != null) {
                    SmtpMailSender.sendEmailWithCc(account, to, cc, bcc, subject, body)
                } else {
                    SmtpMailSender.sendEmail(account, to, subject, body)
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
                        is SendResult.Error -> Toast.makeText(this@ComposeActivity, result.message, Toast.LENGTH_LONG).show()
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
    
    override fun onBackPressed() { finish() }
}
