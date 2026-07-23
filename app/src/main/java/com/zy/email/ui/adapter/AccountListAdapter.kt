package com.zy.email.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.zy.email.databinding.ItemAccountBinding
import com.zy.email.data.model.Account

/**
 * 账户列表适配器
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
            binding.tvAccountEmail.text = account.email
            binding.tvAccountType.text = when (account.accountType) {
                com.zy.email.data.model.AccountType.SMTP -> "SMTP"
                com.zy.email.data.model.AccountType.OAUTH2 -> "OAuth2"
            }
            
            binding.root.setOnClickListener {
                onItemClick(account)
            }
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
