package com.zy.email.ui.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zy.email.R
import com.zy.email.data.repository.EmailMessage
import com.zy.email.databinding.ItemMessageBinding
import com.zy.email.ui.setVisible
import com.zy.email.utils.DateFormatUtils
import com.zy.email.utils.EmailUtils

/**
 * 邮件列表适配器。
 *
 * 未读状态用三重信号表达，避免只靠颜色区分（对色觉障碍用户更友好）：
 *  1. 发件人加粗
 *  2. 主色未读圆点
 *  3. 卡片使用高一级的表面容器（surface_container vs surface_container_low）
 * 所有颜色均取自语义令牌，深色模式下自动反转。
 */
class MessageListAdapter(
    private val onItemClick: (EmailMessage) -> Unit
) : ListAdapter<EmailMessage, MessageListAdapter.MessageViewHolder>(MessageDiffCallback()) {

    /**
     * 已发送模式：列表主标题展示收件人而非发件人（发件人是用户自己）。
     * 由调用方在切换文件夹时设置，再提交新列表。
     */
    var sentMode: Boolean = false
        private set

    fun setSentMode(value: Boolean) {
        if (sentMode == value) return
        sentMode = value
        notifyDataSetChanged()
    }

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
            val context = binding.root.context

            // 收件箱展示发件人；已发送展示收件人（主标题语义为"对方是谁"）
            val correspondentRaw = if (sentMode) message.to else message.from
            val correspondent = EmailUtils.displayNameOf(correspondentRaw)
                .ifBlank {
                    context.getString(
                        if (sentMode) R.string.message_unknown_recipient
                        else R.string.message_unknown_sender
                    )
                }
            binding.tvFrom.text = correspondent
            binding.tvAvatar.text = EmailUtils.initialOf(correspondentRaw)

            binding.tvSubject.text = message.subject.ifBlank {
                context.getString(R.string.message_no_subject)
            }

            val dateText = DateFormatUtils.forList(message.date)
            binding.tvDate.text = dateText
            binding.tvDate.setVisible(dateText.isNotEmpty())

            // 预览：纯文本为空时用 HTML 反推，绝不把标签直接显示出来
            val preview = message.bodyText.trim()
                .ifEmpty { EmailUtils.htmlToPlainText(message.bodyHtml) }
            binding.tvPreview.text = preview.ifEmpty {
                context.getString(R.string.message_no_preview)
            }

            val unread = !message.isRead
            binding.ivUnread.setVisible(unread)

            binding.tvFrom.setTypeface(null, if (unread) Typeface.BOLD else Typeface.NORMAL)
            binding.tvFrom.setTextColor(ContextCompat.getColor(context, R.color.m3_on_surface))

            binding.tvSubject.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (unread) R.color.m3_on_surface else R.color.m3_on_surface_variant
                )
            )
            binding.tvPreview.setTextColor(
                ContextCompat.getColor(context, R.color.m3_on_surface_variant)
            )

            binding.cardRoot.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (unread) R.color.m3_surface_container else R.color.m3_surface_container_low
                )
            )

            binding.ivAttachment.setVisible(message.hasAttachments)

            binding.root.setOnClickListener { onItemClick(message) }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<EmailMessage>() {

        override fun areItemsTheSame(oldItem: EmailMessage, newItem: EmailMessage): Boolean {
            // 优先用文件夹 UID（稳定唯一）；UID 缺失（部分服务器不返回）时
            // 退化为内容指纹
            return if (oldItem.uid > 0 && newItem.uid > 0) {
                oldItem.uid == newItem.uid
            } else if (oldItem.messageId != null && newItem.messageId != null) {
                oldItem.messageId == newItem.messageId
            } else {
                oldItem.from == newItem.from &&
                        oldItem.subject == newItem.subject &&
                        oldItem.date == newItem.date
            }
        }

        override fun areContentsTheSame(oldItem: EmailMessage, newItem: EmailMessage): Boolean {
            return oldItem == newItem
        }
    }
}
