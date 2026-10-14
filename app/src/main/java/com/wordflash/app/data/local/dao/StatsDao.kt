package com.wordflash.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wordflash.app.data.local.entity.DailyStats
import kotlinx.coroutines.flow.Flow

/**
 * 每日汇总统计。
 *
 * 写入采用「先查后改再写回」的方式（见 repository），
 * 而不是 SQL 层的 UPSERT —— 因为累加逻辑（reviewCount + n）
 * 需要先知道当前值，用 SQL 表达会变成难读的长语句。
 */
@Dao
interface StatsDao {

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getStats(date: String): DailyStats?

    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun observeStats(date: String): Flow<DailyStats?>

    /** 最近 N 天的记录（含今天），按日期倒序 */
    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT :days")
    fun observeRecent(days: Int): Flow<List<DailyStats>>

    @Query(
        """
        SELECT * FROM daily_stats
        WHERE date BETWEEN :fromDate AND :toDate
        ORDER BY date ASC
        """
    )
    suspend fun getRange(fromDate: String, toDate: String): List<DailyStats>

    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): DailyStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: DailyStats)

    @Update
    suspend fun update(stats: DailyStats)

    /** 累计学习天数（打卡天数） */
    @Query("SELECT COUNT(*) FROM daily_stats WHERE review_count > 0")
    fun observeStudyDays(): Flow<Int>

    /** 累计学习总时长（毫秒） */
    @Query("SELECT COALESCE(SUM(duration_ms), 0) FROM daily_stats")
    fun observeTotalDuration(): Flow<Long>

    /** 累计学习总词次 */
    @Query("SELECT COALESCE(SUM(review_count), 0) FROM daily_stats")
    fun observeTotalReviews(): Flow<Int>

    // ---- 一次性查询（统计页等非响应式场景）----

    @Query("SELECT COALESCE(SUM(review_count), 0) FROM daily_stats")
    suspend fun getTotalReviewsOnce(): Int

    @Query("SELECT COALESCE(SUM(duration_ms), 0) FROM daily_stats")
    suspend fun getTotalDurationOnce(): Long

    @Query("SELECT COALESCE(SUM(correct_count), 0) FROM daily_stats")
    suspend fun getTotalCorrectOnce(): Int

    @Query("SELECT COUNT(*) FROM daily_stats WHERE review_count > 0")
    suspend fun getStudyDaysOnce(): Int

    /** 累计答对次数，用于算总正确率 */
    @Query("SELECT COALESCE(SUM(correct_count), 0) FROM daily_stats")
    fun observeTotalCorrect(): Flow<Int>

    /**
     * 连续打卡天数所需的数据：取出所有有学习记录的日期，倒序。
     * 连续天数在 repository 里按日期推算 —— 用 SQL 算连续区间
     * 需要窗口函数，Room 支持但可读性差、也不好测试。
     */
    @Query("SELECT date FROM daily_stats WHERE review_count > 0 ORDER BY date DESC")
    suspend fun getCheckedInDates(): List<String>

    @Query("DELETE FROM daily_stats")
    suspend fun clearAll()
}
