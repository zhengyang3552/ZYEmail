package com.zy.email.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.zy.email.databinding.ActivityMessageDetailBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * 邮件详情界面
 */
class MessageDetailActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMessageDetailBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessageDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
        loadMessage()
    }
    
    private fun setupViews() {
        binding.tvReply.setOnClickListener {
            Toast.makeText(this, "回复功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        binding.tvReplyAll.setOnClickListener {
            Toast.makeText(this, "全部回复功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        binding.tvForward.setOnClickListener {
            Toast.makeText(this, "转发功能开发中", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadMessage() {
        val subject = intent.getStringExtra("subject") ?: ""
        val from = intent.getStringExtra("from") ?: ""
        val to = intent.getStringExtra("to") ?: ""
        val dateMillis = intent.getLongExtra("date", 0)
        val bodyHtml = intent.getStringExtra("bodyHtml") ?: ""
        val bodyText = intent.getStringExtra("bodyText") ?: ""
        
        // 设置主题
        binding.tvSubject.text = subject
        
        // 设置发件人
        binding.tvFrom.text = from
        
        // 设置收件人
        binding.tvTo.text = "收件人: $to"
        
        // 设置日期
        if (dateMillis > 0) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            binding.tvDate.text = sdf.format(Date(dateMillis))
        }
        
        // 设置正文
        if (bodyHtml.isNotEmpty()) {
            try {
                val htmlContent = "<html><body style=\"font-family:sans-serif;line-height:1.6;padding:8px;\">${bodyHtml}</body></html>"
                binding.wvBody.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            } catch (e: Exception) {
                binding.tvBody.text = bodyText
            }
        } else {
            binding.tvBody.text = bodyText
        }
        
        // 工具栏返回
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
}
