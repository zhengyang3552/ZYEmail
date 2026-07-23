package com.zy.email.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zy.email.data.AccountTypeConverters
import com.zy.email.data.model.Account

@Database(entities = [Account::class], version = 1, exportSchema = false)
@TypeConverters(AccountTypeConverters::class)
abstract class EmailDatabase : RoomDatabase() {
    
    abstract fun accountDao(): AccountDao
    
    companion object {
        @Volatile
        private var INSTANCE: EmailDatabase? = null
        
        fun getInstance(context: Context): EmailDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EmailDatabase::class.java,
                    "email_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
