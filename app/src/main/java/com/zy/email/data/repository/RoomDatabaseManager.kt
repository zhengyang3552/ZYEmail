package com.zy.email.data.repository

import android.content.Context
import com.zy.email.data.database.EmailDatabase
import com.zy.email.data.model.Account
import kotlinx.coroutines.flow.Flow

object RoomDatabaseManager {
    
    private var appContext: Context? = null
    
    fun init(context: Context) {
        appContext = context.applicationContext
    }
    
    private fun getDao() = EmailDatabase.getInstance(
        appContext ?: throw IllegalStateException("RoomDatabaseManager not initialized. Call init() first.")
    ).accountDao()
    
    fun getAllAccounts(): Flow<List<Account>> = getDao().getAllAccounts()
    
    suspend fun getAccountById(id: Long): Account? = getDao().getAccountById(id)
    
    suspend fun getAccountByEmail(email: String): Account? = getDao().getAccountByEmail(email)
    
    suspend fun insertAccount(account: Account): Long = getDao().insertAccount(account)
    
    suspend fun updateAccount(account: Account) = getDao().updateAccount(account)
    
    suspend fun deleteAccount(account: Account) = getDao().deleteAccount(account)
}
