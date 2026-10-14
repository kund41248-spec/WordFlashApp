package com.wordflash.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 日期时间工具。
 *
 * 统一以「本地时区」为准 —— 用户感知的「今天」是本机的今天，
 * 而不是 UTC 的今天。跨时区使用会导致打卡日错位。
 */
object DateUtils {

    private val DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val DISPLAY_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日")

    private fun zone(): ZoneId = ZoneId.systemDefault()

    /** 今天的日期串 yyyy-MM-dd */
    fun today(): String = formatDate(System.currentTimeMillis())

    /** 时间戳 -> yyyy-MM-dd */
    fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().format(DATE_FORMATTER)

    /** yyyy-MM-dd -> 当天 00:00 的时间戳 */
    fun parseDate(date: String): Long =
        LocalDate.parse(date, DATE_FORMATTER)
            .atStartOfDay(zone())
            .toInstant()
            .toEpochMilli()

    /** 展示用短日期，如「10月14日」 */
    fun toDisplayDate(date: String): String = runCatching {
        LocalDate.parse(date, DATE_FORMATTER).format(DISPLAY_FORMATTER)
    }.getOrDefault(date)

    /** 相对时间描述，用于「下次复习」提示 */
    fun toRelative(millis: Long, now: Long = System.currentTimeMillis()): String {
        val diff = millis - now
        if (diff <= 0) return "现在"
        val minutes = diff / 60_000
        val hours = diff / 3_600_000
        val days = diff / 86_400_000
        return when {
            minutes < 60 -> "${minutes}分钟后"
            hours < 24 -> "${hours}小时后"
            days < 30 -> "${days}天后"
            else -> "${days / 30}个月后"
        }
    }

    /** 把毫秒时长格式化为「1小时23分」或「45秒」 */
    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}小时${minutes}分"
            minutes > 0 -> "${minutes}分${seconds}秒"
            else -> "${seconds}秒"
        }
    }
}
