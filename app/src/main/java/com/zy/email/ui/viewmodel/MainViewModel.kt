package com.zy.email.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zy.email.data.model.Account
import com.zy.email.data.repository.EmailRepository
import com.zy.email.data.repository.ImapMailboxManager
import com.zy.email.data.repository.RoomDatabaseManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 主界面ViewModel
 */
class MainViewModel : ViewModel() {
    
    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()
    
    private val _messages = MutableStateFlow<List<EmailMessage>>(emptyList())
    val messages: StateFlow<List<EmailMessage>> = _messages.asStateFlow()
    
    init {
        loadAccounts()
    }
    
    /**
     * 加载所有账户
     */
    fun loadAccounts() {
        viewModelScope.launch {
            RoomDatabaseManager.getAllAccounts().collect { accountList ->
                _accounts.value = accountList
            }
        }
    }
    
    /**
     * 加载指定账户的邮件
     */
    fun loadMessages(accountId: Long) {
        viewModelScope.launch {
            val account = RoomDatabaseManager.getAccountById(accountId)
            if (account != null) {
                val store = ImapMailboxManager.getStore(account)
                if (store != null) {
                    val folder = ImapMailboxManager.getFolder(store)
                    if (folder != null) {
                        val messageList = ImapMailboxManager.getMessages(folder)
                        _messages.value = messageList
                        
                        // 标记已读
                        for (msg in messageList) {
                            // TODO: 标记为已读
                        }
                        
                        ImapMailboxManager.closeFolder(folder)
                        ImapMailboxManager.closeStore(store)
                    }
                }
            }
        }
    }
}
