package com.zy.email.ui.theme

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.color.DynamicColors
import com.zy.email.R

/**
 * 明暗模式与动态配色控制。
 *
 * 外观模式只负责"用户偏好 → AppCompatDelegate"这一段映射，具体的颜色差异全部由
 * `values/colors.xml` 与 `values-night/colors.xml` 中的同名令牌承担，
 * 因此切换模式时不需要在任何界面里写 if-else。
 *
 * 动态配色（Android 12+ 壁纸取色，参考 rikkahub 的"壁纸取色 + 种子色回退"）：
 * 开启后由 Material 组件的 DynamicColors 在 API 31+ 用壁纸色替换品牌色，
 * 低版本自动忽略注册、继续使用 values/ 下的统一种子色方案。
 */
object ThemeManager {

    /** 跟随系统（AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM） */
    const val MODE_SYSTEM = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    /** 始终浅色 */
    const val MODE_LIGHT = AppCompatDelegate.MODE_NIGHT_NO

    /** 始终深色 */
    const val MODE_DARK = AppCompatDelegate.MODE_NIGHT_YES

    private const val PREFS_NAME = "zy_email_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"

    private val availableModes = intArrayOf(MODE_SYSTEM, MODE_LIGHT, MODE_DARK)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 读取已保存的外观偏好。
     *
     * 加了一层白名单校验：SharedPreferences 里的值可能来自旧版本或被外部改写，
     * 非法值会让 setDefaultNightMode 抛异常，因此统一收敛为"跟随系统"。
     */
    fun currentMode(context: Context): Int {
        val saved = prefs(context).getInt(KEY_THEME_MODE, MODE_SYSTEM)
        return if (saved in availableModes) saved else MODE_SYSTEM
    }

    /** 应用已保存的偏好（在 Application#onCreate 中调用一次即可） */
    fun applySavedMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(currentMode(context))
    }

    /**
     * 保存并立即应用新的外观偏好。
     * setDefaultNightMode 会触发已启动 Activity 的重建，界面无需手动刷新。
     */
    fun setMode(context: Context, mode: Int) {
        val safeMode = if (mode in availableModes) mode else MODE_SYSTEM
        prefs(context).edit().putInt(KEY_THEME_MODE, safeMode).apply()
        AppCompatDelegate.setDefaultNightMode(safeMode)
    }

    /** 当前是否处于深色外观（跟随系统时取系统当前配置） */
    fun isNightMode(context: Context): Boolean {
        return when (currentMode(context)) {
            MODE_DARK -> true
            MODE_LIGHT -> false
            else -> {
                val uiMode = context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    // ---------------------------------------------------------------- 动态配色

    /** 动态取色是否开启（默认关闭，保持统一品牌色） */
    fun dynamicColorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DYNAMIC_COLOR, false)

    /** 保存动态取色偏好（是否立即生效由调用方处理） */
    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    /**
     * 在每个 Activity 的 onCreate 中、super.onCreate 之前调用。
     * Android 12+ 且开关打开时用壁纸取色包装当前主题；其余情况原样返回，
     * 界面继续使用 values/ 下的统一种子色方案。
     * 按需包装（而不是在 Application 全局注册）保证关闭开关后 recreate()
     * 即可立即恢复品牌配色，无需重启应用。
     */
    fun applyDynamicColorIfEnabled(activity: android.app.Activity) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            dynamicColorEnabled(activity)
        ) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    /** 外观偏好在设置页的展示文案 */
    fun labelRes(mode: Int): Int = when (mode) {
        MODE_LIGHT -> R.string.settings_theme_light
        MODE_DARK -> R.string.settings_theme_dark
        else -> R.string.settings_theme_system
    }
}
