package com.wordflash.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 每日学习汇总。**打卡、学习时长、正确率图表都直接查这张表**，
 * 避免每次渲染统计页都去扫全量 [StudyRecord]。
 *
 * 主键是日期字符串（yyyy-MM-dd），天然唯一，便于按天 UPSERT。
 */
@Entity(tableName = "daily_stats")
data class DailyStats(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    /** 当天新学单词数（首次进入 LEARNING 的词） */
    @ColumnInfo(name = "learned_count")
    val learnedCount: Int = 0,

    /** 当天复习次数（含新学，即总作答次数） */
    @ColumnInfo(name = "review_count")
    val reviewCount: Int = 0,

    /** 当天答对次数 */
    @ColumnInfo(name = "correct_count")
    val correctCount: Int = 0,

    /** 当天学习总时长（毫秒） */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    /** 是否已打卡。当天学习任意一次即视为打卡 */
    @ColumnInfo(name = "checked_in")
    val checkedIn: Boolean = false
) {
    /** 当天正确率，0f~1f */
    val accuracy: Float
        get() = if (reviewCount == 0) 0f else correctCount.toFloat() / reviewCount

    /** 学习时长（分钟），四舍五入 */
    val durationMinutes: Int
        get() = (durationMs / 60_000.0).toInt()
}
