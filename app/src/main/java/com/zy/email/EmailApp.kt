package com.zy.email

import android.app.Application
import android.util.Log
import com.zy.email.data.database.EmailDatabase
import com.zy.email.data.repository.RoomDatabaseManager
import com.zy.email.ui.theme.ThemeManager

/**
 * 应用程序入口
 *
 * 初始化顺序有讲究：
 *  1. 先应用外观偏好，保证第一个 Activity 创建时拿到的就是正确的资源目录
 *     （values 或 values-night），避免出现"先亮后暗"的闪烁。
 *  2. 再初始化数据库。数据库创建失败不应让整个应用崩溃——记录日志后
 *     由各界面在访问数据时给出可读提示。
 */
class EmailApp : Application() {

    override fun onCreate() {
        super.onCreate()

        ThemeManager.applySavedMode(this)

        try {
            EmailDatabase.getInstance(this)
            RoomDatabaseManager.init(this)
        } catch (e: Exception) {
            Log.e(TAG, "数据库初始化失败", e)
        }
    }

    private companion object {
        const val TAG = "EmailApp"
    }
}
