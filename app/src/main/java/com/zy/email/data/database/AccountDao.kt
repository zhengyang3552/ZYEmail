package com.zy.email.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zy.email.data.model.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    /**
     * 注意：列名必须与实体属性一致。
     * 原实现写的是 `ORDER BY created_at`，而 [Account] 中字段名为 createdAt，
     * Room 在编译期就会因为找不到该列而报错（整个工程无法构建）。
     */
    @Query("SELECT * FROM accounts ORDER BY createdAt DESC")
    fun getAllAccounts(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): Account?

    @Query("SELECT * FROM accounts WHERE email = :email LIMIT 1")
    suspend fun getAccountByEmail(email: String): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account)

    @Delete
    suspend fun deleteAccount(account: Account)

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()
}
