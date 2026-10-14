package com.wordflash.app.data.repository

import com.wordflash.app.data.local.dao.ProgressDao
import com.wordflash.app.data.local.dao.StatsDao
import com.wordflash.app.data.local.entity.DailyStats
import com.wordflash.app.data.local.entity.StudyRecord
import com.wordflash.app.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统计仓库：记录作答、维护每日汇总、提供图表数据。
 */
@Singleton
class StatsRepository @Inject constructor(
    private val statsDao: StatsDao,
    private val progressDao: ProgressDao
) {

    companion object {
        /** 逐次作答明细的保留天数。超过则清理，避免数据库无限膨胀。 */
        const val RECORD_RETENTION_DAYS = 180
    }

    // ============================================================
    // 写入
    // ============================================================

    /**
     * 记录一次作答。
     *
     * 一次调用同时做两件事：写明细 + 累加当日汇总。
     * 之所以不合并成一条 SQL，是因为「当日汇总不存在则新建、存在则累加」
     * 的逻辑用 Kotlin 表达更清晰，也更好单测。
     *
     * @param isNewLearned 该词是否是首次学习（用于区分 learnedCount 与 reviewCount）
     */
    suspend fun recordAnswer(
        wordId: Long,
        mode: String,
        isCorrect: Boolean,
        durationMs: Long,
        isNewLearned: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val date = DateUtils.formatDate(timestamp)

        progressDao.insertRecord(
            StudyRecord(
                wordId = wordId,
                mode = mode,
                isCorrect = isCorrect,
                durationMs = durationMs,
                timestamp = timestamp,
                studyDate = date
            )
        )

        val current = statsDao.getStats(date) ?: DailyStats(date = date)
        statsDao.upsert(
            current.copy(
                learnedCount = current.learnedCount + if (isNewLearned) 1 else 0,
                reviewCount = current.reviewCount + 1,
                correctCount = current.correctCount + if (isCorrect) 1 else 0,
                durationMs = current.durationMs + durationMs,
                checkedIn = true
            )
        )
    }

    /** 清理过期的作答明细 */
    suspend fun pruneOldRecords(now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - RECORD_RETENTION_DAYS * 24L * 60 * 60 * 1000
        return progressDao.deleteRecordsBefore(cutoff)
    }

    // ============================================================
    // 读取
    // ============================================================

    fun observeToday(): Flow<DailyStats?> =
        statsDao.observeStats(DateUtils.today())

    fun observeRecentDays(days: Int): Flow<List<DailyStats>> =
        statsDao.observeRecent(days)

    fun observeTotalDuration(): Flow<Long> = statsDao.observeTotalDuration()

    fun observeTotalReviews(): Flow<Int> = statsDao.observeTotalReviews()

    fun observeStudyDays(): Flow<Int> = statsDao.observeStudyDays()

    // ---- 一次性查询（用于统计页等非响应式场景）----

    suspend fun getTotalReviewsOnce(): Int = statsDao.getTotalReviewsOnce()

    suspend fun getTotalDurationOnce(): Long = statsDao.getTotalDurationOnce()

    suspend fun getTotalCorrectOnce(): Int = statsDao.getTotalCorrectOnce()

    suspend fun getStudyDaysOnce(): Int = statsDao.getStudyDaysOnce()

    /** 总正确率 0f~1f。两个 Flow 合并后才能算比率。 */
    fun observeOverallAccuracy(): Flow<Float> =
        combine(
            statsDao.observeTotalCorrect(),
            statsDao.observeTotalReviews()
        ) { correct, total ->
            if (total == 0) 0f else correct.toFloat() / total
        }

    /**
     * 连续打卡天数。
     *
     * 算法：取出所有有学习记录的日期（倒序），从今天开始逐日回溯。
     * 今天没学不算断签（可能还没开始学），因此从今天或昨天起算都允许。
     */
    suspend fun calculateStreak(): Int {
        val dates = statsDao.getCheckedInDates()
        if (dates.isEmpty()) return 0

        val today = DateUtils.today()
        val yesterday = DateUtils.formatDate(
            System.currentTimeMillis() - 24L * 60 * 60 * 1000
        )

        // 最近一次学习既不是今天也不是昨天 -> 已断签
        if (dates.first() != today && dates.first() != yesterday) return 0

        val dateSet = dates.toHashSet()
        var streak = 0
        var cursor = if (dates.first() == today) today else yesterday

        while (dateSet.contains(cursor)) {
            streak++
            cursor = DateUtils.formatDate(DateUtils.parseDate(cursor) - 24L * 60 * 60 * 1000)
        }
        return streak
    }

    suspend fun getLatest(): DailyStats? = statsDao.getLatest()

    suspend fun getRange(from: String, to: String): List<DailyStats> =
        statsDao.getRange(fromDate = from, toDate = to)

    // ============================================================
    // 清空（设置页的「清除数据」）
    // ============================================================

    suspend fun clearAll() {
        statsDao.clearAll()
        progressDao.clearAllRecords()
    }
}
