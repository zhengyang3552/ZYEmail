package com.zy.email.utils

import android.content.Context

/**
 * 应用级轻量偏好（与 ThemeManager 的 zy_email_settings 分开，避免职责混杂）。
 *
 * login_skipped：用户在登录页点过"稍后再说"（或系统返回退出登录页）。
 * 之后冷启动不再强制跳登录页，直接进主界面——主界面的空账户引导页
 * 提供"添加账户"入口，用户随时可自行登录。
 * 一旦登录成功（存在账户），路由以账户为准，此标记不再参与判断。
 */
object AppPrefs {

    private const val PREFS_NAME = "zy_email_app"
    private const val KEY_LOGIN_SKIPPED = "login_skipped"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 用户是否已在登录页选择过"稍后再说" */
    fun wasLoginSkipped(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOGIN_SKIPPED, false)

    fun setLoginSkipped(context: Context, skipped: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOGIN_SKIPPED, skipped).apply()
    }
}
