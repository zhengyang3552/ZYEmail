package com.zy.email.ui

import android.view.View
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import com.zy.email.R

/** 显示 / 隐藏（不占用布局空间） */
fun View.setVisible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

/**
 * 统一给工具栏菜单图标着色。
 *
 * 图标资源本身是白色填充，若不做着色，在浅色主题的白色工具栏上会"消失"。
 * 使用主题语义色而非硬编码颜色，保证深浅两种模式都清晰可见。
 */
fun MaterialToolbar.tintMenuIcons(@ColorRes colorRes: Int) {
    val color = ContextCompat.getColor(context, colorRes)
    val currentMenu = menu ?: return
    for (index in 0 until currentMenu.size()) {
        currentMenu.getItem(index).icon?.mutate()?.setTint(color)
    }
}

/**
 * 语义化的成功反馈。
 *
 * 默认 Snackbar 使用反色表面，无法表达"成功"这一语义；这里改用 success 容器色，
 * 与错误提示（error 色）形成明确区分，且两套配色在明暗模式下都保持对比度。
 */
fun View.showSuccess(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_SHORT)
        .setBackgroundTint(ContextCompat.getColor(context, R.color.m3_success_container))
        .setTextColor(ContextCompat.getColor(context, R.color.m3_on_success_container))
        .show()
}
