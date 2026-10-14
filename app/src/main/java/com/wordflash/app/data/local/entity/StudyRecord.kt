package com.wordflash.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 逐次作答明细。
 *
 * 这张表会持续增长，因此：
 * - 只保留必要字段，不冗余存单词内容
 * - 为 timestamp 建索引，便于按时间范围查询和清理
 * - 在 [com.wordflash.app.data.repository.StatsRepository] 中提供
 *   按天聚合进 [DailyStats]，以及清理历史数据的方法
 */
@Entity(
    tableName = "study_record",
    foreignKeys = [
        ForeignKey(
            entity = Word::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["word_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["study_date"])
    ]
)
data class StudyRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "word_id")
    val wordId: Long,

    /** 学习模式，见 [com.wordflash.app.domain.model.StudyMode] */
    @ColumnInfo(name = "mode")
    val mode: String,

    /** 是否答对 */
    @ColumnInfo(name = "is_correct")
    val isCorrect: Boolean,

    /** 本次作答耗时（毫秒），用于统计平均反应速度 */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    /** 作答时间戳 */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * 作答当天日期，格式 yyyy-MM-dd。
     * 冗余存储是为了让「按天聚合」能用上索引，避免对 timestamp 做函数运算导致索引失效。
     */
    @ColumnInfo(name = "study_date")
    val studyDate: String
)
