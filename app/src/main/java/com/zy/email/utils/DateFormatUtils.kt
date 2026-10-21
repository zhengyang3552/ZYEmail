package com.zy.email.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 邮件时间格式化。
 *
 * 列表里只给"判断新旧所需要的最小信息"，详情里才给完整时间——这是邮件类
 * 应用中通行的信息密度策略，也避免了原实现里列表统一显示 `MM-dd HH:mm`
 * 而看不出"是否今天"的问题。
 */
object DateFormatUtils {

    private const val YESTERDAY = "昨天"

    /** 列表用的紧凑格式：今天→时刻，昨天→昨天，今年→月日，更早→年月日 */
    fun forList(date: Date?): String {
        if (date == null || date.time <= 0L) return ""
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }

        val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
        val dayDiff = if (sameYear) {
            now.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR)
        } else {
            Int.MIN_VALUE
        }

        return when {
            dayDiff == 0 -> format(date, "HH:mm")
            dayDiff == 1 -> YESTERDAY
            sameYear -> format(date, "M月d日")
            else -> format(date, "yyyy/M/d")
        }
    }

    /** 详情页用的完整格式 */
    fun forDetail(date: Date?): String {
        if (date == null || date.time <= 0L) return ""
        return format(date, "yyyy年M月d日 HH:mm")
    }

    /** 引用原文时的格式 */
    fun forQuote(date: Date?): String {
        if (date == null || date.time <= 0L) return ""
        return format(date, "yyyy-MM-dd HH:mm")
    }

    private fun format(date: Date, pattern: String): String {
        // SimpleDateFormat 非线程安全，且列表/详情使用不同 pattern，
        // 这里按调用创建实例，代价远小于共享实例带来的并发风险。
        return try {
            SimpleDateFormat(pattern, Locale.getDefault()).format(date)
        } catch (e: Exception) {
            ""
        }
    }
}
