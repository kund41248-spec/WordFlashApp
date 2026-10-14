package com.wordflash.app.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Room 类型转换器。
 *
 * 时间统一用 epoch millis（Long）存储 —— 与时区无关，比较和排序都直接。
 * 日期用 yyyy-MM-dd 字符串存储 —— 人类可读，且能作为主键。
 */
class Converters {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // ---------- LocalDate <-> String ----------

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? =
        date?.format(dateFormatter)

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? =
        value?.let { LocalDate.parse(it, dateFormatter) }

    // ---------- Instant <-> Long ----------

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? =
        instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(millis: Long?): Instant? =
        millis?.let { Instant.ofEpochMilli(it) }

    // ---------- Long 时间戳 -> LocalDate（辅助，非 Room 必需） ----------

    fun millisToLocalDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
