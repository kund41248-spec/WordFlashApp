package com.wordflash.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单词的掌握状态。艾宾浩斯调度的核心数据。
 */
object WordState {
    /** 从未学习过 */
    const val NEW = 0

    /** 学习中：刚接触，尚未形成稳定记忆 */
    const val LEARNING = 1

    /** 复习中：已进入长间隔复习队列 */
    const val REVIEWING = 2

    /** 已掌握：间隔拉到最长，通常不再主动出现 */
    const val MASTERED = 3
}

/**
 * 学习进度。**每个单词一条记录**，用 [wordId] 直接做主键。
 *
 * 采用 wordId 作主键而非自增 id，好处是更新进度时
 * 直接 INSERT OR REPLACE 即可，不必先查询再判断插入还是更新。
 *
 * 字段含义与 SM-2 算法对应：
 * - [repetitions] 连续答对次数，答错清零
 * - [intervalDays] 当前复习间隔（天）
 * - [easeFactor] 难度系数 1.3~2.8，越大表示越容易，间隔增长越快
 * - [nextReviewAt] 下次复习时间戳（毫秒）
 */
@Entity(
    tableName = "word_progress",
    foreignKeys = [
        ForeignKey(
            entity = Word::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["next_review_at"]),
        Index(value = ["state"]),
        Index(value = ["is_in_wrong_book"])
    ]
)
data class WordProgress(
    @PrimaryKey
    @ColumnInfo(name = "word_id")
    val wordId: Long,

    /** 见 [WordState] */
    @ColumnInfo(name = "state")
    val state: Int = WordState.NEW,

    /** 连续答对次数 */
    @ColumnInfo(name = "repetitions")
    val repetitions: Int = 0,

    /** 当前复习间隔（天）。为 0 表示仍在当天内重复 */
    @ColumnInfo(name = "interval_days")
    val intervalDays: Int = 0,

    /** 难度系数，范围 1.3 ~ 2.8 */
    @ColumnInfo(name = "ease_factor")
    val easeFactor: Float = 2.5f,

    /** 下次复习时间（epoch millis） */
    @ColumnInfo(name = "next_review_at")
    val nextReviewAt: Long = 0L,

    /** 上次复习时间 */
    @ColumnInfo(name = "last_review_at")
    val lastReviewAt: Long = 0L,

    /** 累计答对次数 */
    @ColumnInfo(name = "correct_count")
    val correctCount: Int = 0,

    /** 累计答错次数。**只增不减**，用于统计正确率 */
    @ColumnInfo(name = "wrong_count")
    val wrongCount: Int = 0,

    /** 累计学习次数 */
    @ColumnInfo(name = "review_count")
    val reviewCount: Int = 0,

    /**
     * 最近一次答错的时间（epoch millis）。0 表示从未答错。
     * 错题本按此字段倒序排列，让"刚错的"排在前面。
     */
    @ColumnInfo(name = "last_wrong_at")
    val lastWrongAt: Long = 0L,

    /**
     * 是否在错题本中。
     *
     * 为什么不直接用 `wrongCount > 0` 判断：
     * [wrongCount] 是**累计统计值**，只增不减（否则正确率会失真）；
     * 而错题本的成员资格需要能被"答对"清除。
     * 两者语义不同，所以用一个独立标志位。
     */
    @ColumnInfo(name = "is_in_wrong_book")
    val isInWrongBook: Boolean = false
) {
    /** 正确率，0f~1f。从未学习时返回 0 */
    val accuracy: Float
        get() {
            val total = correctCount + wrongCount
            return if (total == 0) 0f else correctCount.toFloat() / total
        }
}
