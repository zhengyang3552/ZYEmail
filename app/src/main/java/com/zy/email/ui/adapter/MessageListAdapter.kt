package com.zy.email.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zy.email.R
import com.zy.email.databinding.ItemMessageBinding
import com.zy.email.data.repository.EmailMessage
import java.text.SimpleDateFormat
import java.util.*

/**
 * 邮件列表适配器
 */
class MessageListAdapter(
    private val onItemClick: (EmailMessage) -> Unit
) : ListAdapter<EmailMessage, MessageListAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class MessageViewHolder(
        private val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(message: EmailMessage) {
            // 发件人
            binding.tvFrom.text = message.from
            
            // 主题
            binding.tvSubject.text = message.subject
            
            // 预览（去掉HTML标签）
            val preview = message.bodyText.replace(Regex("<[^>]*>"), "").take(50)
            binding.tvPreview.text = if (preview.isEmpty()) "无内容" else preview
            
            // 日期
            message.date?.let {
                binding.tvDate.text = dateFormat.format(it)
            }
            
            // 未读标记
            if (!message.isRead) {
                binding.tvFrom.setTextColor(binding.root.context.getColor(R.color.unread_from))
                binding.tvSubject.setTextColor(binding.root.context.getColor(R.color.unread_subject))
                binding.ivUnread.visibility = android.view.View.VISIBLE
            } else {
                binding.tvFrom.setTextColor(binding.root.context.getColor(R.color.read_from))
                binding.tvSubject.setTextColor(binding.root.context.getColor(R.color.read_subject))
                binding.ivUnread.visibility = android.view.View.GONE
            }
            
            // 附件标记
            binding.ivAttachment.visibility = if (message.hasAttachments) 
                android.view.View.VISIBLE else android.view.View.GONE
            
            // 点击事件
            binding.root.setOnClickListener {
                onItemClick(message)
            }
        }
    }
    
    class MessageDiffCallback : DiffUtil.ItemCallback<EmailMessage>() {
        override fun areItemsTheSame(oldItem: EmailMessage, newItem: EmailMessage): Boolean {
            return oldItem.messageId == newItem.messageId
        }
        
        override fun areContentsTheSame(oldItem: EmailMessage, newItem: EmailMessage): Boolean {
            return oldItem == newItem
        }
    }
}
