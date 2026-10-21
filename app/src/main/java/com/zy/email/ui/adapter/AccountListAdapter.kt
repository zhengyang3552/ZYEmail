package com.zy.email.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zy.email.R
import com.zy.email.data.model.Account
import com.zy.email.data.model.AccountType
import com.zy.email.databinding.ItemAccountBinding

/**
 * 账户列表适配器。
 * 次要信息改为展示"协议 + 服务器"，比原实现只显示 "SMTP"/"OAuth2"
 * 更有助于用户确认账户配置是否正确。
 */
class AccountListAdapter(
    private val onItemClick: (Account) -> Unit
) : ListAdapter<Account, AccountListAdapter.AccountViewHolder>(AccountDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemAccountBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AccountViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AccountViewHolder(
        private val binding: ItemAccountBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(account: Account) {
            val context = binding.root.context
            binding.tvAccountEmail.text = account.email

            binding.tvAccountType.text = when (account.accountType) {
                AccountType.SMTP -> context.getString(
                    R.string.settings_account_type_smtp,
                    account.imapServer.ifBlank { account.smtpServer }
                )
                AccountType.OAUTH2 -> context.getString(R.string.settings_account_type_oauth2)
            }

            binding.root.setOnClickListener { onItemClick(account) }
        }
    }

    class AccountDiffCallback : DiffUtil.ItemCallback<Account>() {
        override fun areItemsTheSame(oldItem: Account, newItem: Account): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Account, newItem: Account): Boolean {
            return oldItem == newItem
        }
    }
}
