package com.zy.email.data

import androidx.room.TypeConverter
import com.zy.email.data.model.AccountType

/**
 * Room 类型转换器
 * 用于将非基本类型转换为可存储的类型
 */
class AccountTypeConverters {

    @TypeConverter
    fun fromAccountType(accountType: AccountType): String {
        return accountType.name
    }

    @TypeConverter
    fun toAccountType(value: String): AccountType {
        return AccountType.valueOf(value)
    }
}
