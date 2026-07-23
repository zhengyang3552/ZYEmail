package com.zy.email

import android.app.Application
import com.zy.email.data.database.EmailDatabase
import com.zy.email.data.repository.RoomDatabaseManager

/**
 * 应用程序入口
 * 初始化全局组件
 */
class EmailApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 初始化数据库（单例）
        @Suppress("UnusedPrivateMember")
        val db = EmailDatabase.getInstance(this)
        
        // 初始化RoomDatabaseManager
        RoomDatabaseManager.init(this)
    }
}
