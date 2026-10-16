package com.wordflash.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wordflash.app.data.local.entity.StudyRecord
import com.wordflash.app.data.local.entity.WordProgress
import com.wordflash.app.data.local.entity.WordState
import com.wordflash.app.domain.model.WordWithProgress
import kotlinx.coroutines.flow.Flow

/**
 * 学习进度与作答记录。
 *
 * ## 关于重复的列清单
 *
 * 下面多处查询都要把 `word_progress` 的全部字段以 `p_` 前缀选出来，
 * 才能被 [WordWithProgress] 的 `@Embedded(prefix = "p_")` 正确映射。
 *
 * 这段清单**必须整体同步修改** —— 实体加一个字段，
 * 所有联表查询都要补上对应列，否则 Room 会在编译期报
 * "The columns returned by the query does not have the fields ..."。
 *
 * 之所以没用 `const val` 抽出来复用：`@Query` 的参数要求是
 * 编译期常量，而 Kotlin 注解里对字符串拼接的支持在各版本上表现不一致，
 * 显式写全虽然啰嗦，但行为最确定、也便于直接复制到数据库工具里调试。
 */
@Dao
interface ProgressDao {

    // ============================================================
    // 进度读写
    // ============================================================

    @Query("SELECT * FROM word_progress WHERE word_id = :wordId")
    suspend fun getProgress(wordId: Long): WordProgress?

    @Query("SELECT * FROM word_progress WHERE word_id IN (:wordIds)")
    suspend fun getProgressFor(wordIds: List<Long>): List<WordProgress>

    /** 用 REPLACE 实现 UPSERT：wordId 是主键，存在即覆盖 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: WordProgress)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgressAll(list: List<WordProgress>)

    // ============================================================
    // 待复习队列
    // ============================================================

    /**
     * 到期待复习的单词。
     *
     * [now] 由调用方传入，便于单元测试固定时间。
     */
    @Transaction
    @Query(
        """
        SELECT w.*, p.word_id AS p_word_id, p.state AS p_state,
               p.repetitions AS p_repetitions, p.interval_days AS p_interval_days,
               p.ease_factor AS p_ease_factor, p.next_review_at AS p_next_review_at,
               p.last_review_at AS p_last_review_at,
               p.correct_count AS p_correct_count, p.wrong_count AS p_wrong_count,
               p.review_count AS p_review_count,
               p.last_wrong_at AS p_last_wrong_at, p.is_in_wrong_book AS p_is_in_wrong_book
        FROM word w
        INNER JOIN word_progress p ON w.id = p.word_id
        WHERE w.book_id = :bookId
          AND p.next_review_at <= :now
          AND p.state != :masteredState
        ORDER BY p.next_review_at ASC, p.state ASC
        LIMIT :limit
        """
    )
    suspend fun getDueWords(
        bookId: Long,
        now: Long,
        limit: Int,
        masteredState: Int = WordState.MASTERED
    ): List<WordWithProgress>

    /**
     * 该词库中从未学习过的新词。
     *
     * 外部 LEFT JOIN 未命中时这些列全是 NULL，
     * 因此显式写 NULL 而不是依赖 `p.xxx` —— 这样 [WordWithProgress.progress]
     * 一定是 null，`isNew` 判定才可靠。
     */
    @Transaction
    @Query(
        """
        SELECT w.*, NULL AS p_word_id, NULL AS p_state,
               NULL AS p_repetitions, NULL AS p_interval_days,
               NULL AS p_ease_factor, NULL AS p_next_review_at,
               NULL AS p_last_review_at,
               NULL AS p_correct_count, NULL AS p_wrong_count,
               NULL AS p_review_count,
               NULL AS p_last_wrong_at, NULL AS p_is_in_wrong_book
        FROM word w
        LEFT JOIN word_progress p ON w.id = p.word_id
        WHERE w.book_id = :bookId AND p.word_id IS NULL
        ORDER BY w.id ASC
        LIMIT :limit
        """
    )
    suspend fun getNewWords(bookId: Long, limit: Int): List<WordWithProgress>

    /** 到期待复习数量（不含新词） */
    @Query(
        """
        SELECT COUNT(*) FROM word_progress p
        INNER JOIN word w ON w.id = p.word_id
        WHERE w.book_id = :bookId AND p.next_review_at <= :now AND p.state != :mastered
        """
    )
    fun observeDueCount(bookId: Long, now: Long, mastered: Int = WordState.MASTERED): Flow<Int>

    /** 到期待复习数量的一次性查询，用于词库列表批量刷新 */
    @Query(
        """
        SELECT COUNT(*) FROM word_progress p
        INNER JOIN word w ON w.id = p.word_id
        WHERE w.book_id = :bookId AND p.next_review_at <= :now AND p.state != :mastered
        """
    )
    suspend fun countDueNow(bookId: Long, now: Long, mastered: Int = WordState.MASTERED): Int

    /** 跨全部词库的到期数量，首页总览用 */
    @Query(
        """
        SELECT COUNT(*) FROM word_progress
        WHERE next_review_at <= :now AND state != :mastered
        """
    )
    fun observeTotalDueCount(now: Long, mastered: Int = WordState.MASTERED): Flow<Int>

    /** 某词库各状态的单词数统计 */
    @Query(
        """
        SELECT p.state AS state, COUNT(*) AS count
        FROM word_progress p
        INNER JOIN word w ON w.id = p.word_id
        WHERE w.book_id = :bookId
        GROUP BY p.state
        """
    )
    suspend fun getStateCounts(bookId: Long): List<StateCount>

    /** 已掌握词数 */
    @Query("SELECT COUNT(*) FROM word_progress WHERE state = :state")
    fun observeCountByState(state: Int): Flow<Int>

    /** 未来 N 天的复习量预测 */
    @Query(
        """
        SELECT (next_review_at / 86400000) * 86400000 AS dayStart, COUNT(*) AS count
        FROM word_progress
        WHERE next_review_at BETWEEN :from AND :to AND state != :mastered
        GROUP BY dayStart
        ORDER BY dayStart ASC
        """
    )
    suspend fun getUpcomingReviewLoad(
        from: Long,
        to: Long,
        mastered: Int = WordState.MASTERED
    ): List<DayLoad>

    // ============================================================
    // 错题本
    // ============================================================

    /**
     * 错题本列表，按「最近答错时间」倒序 —— 刚错的排最前面。
     */
    @Transaction
    @Query(
        """
        SELECT w.*, p.word_id AS p_word_id, p.state AS p_state,
               p.repetitions AS p_repetitions, p.interval_days AS p_interval_days,
               p.ease_factor AS p_ease_factor, p.next_review_at AS p_next_review_at,
               p.last_review_at AS p_last_review_at,
               p.correct_count AS p_correct_count, p.wrong_count AS p_wrong_count,
               p.review_count AS p_review_count,
               p.last_wrong_at AS p_last_wrong_at, p.is_in_wrong_book AS p_is_in_wrong_book
        FROM word w
        INNER JOIN word_progress p ON w.id = p.word_id
        WHERE p.is_in_wrong_book = 1
        ORDER BY p.last_wrong_at DESC
        """
    )
    fun observeWrongBook(): Flow<List<WordWithProgress>>

    /** 错题本数量，用于 tab 角标 */
    @Query("SELECT COUNT(*) FROM word_progress WHERE is_in_wrong_book = 1")
    fun observeWrongBookCount(): Flow<Int>

    /**
     * 错题本里的词，用于「专项练习」组卷。
     * 与列表用同一个排序，保证练的就是最先看到的那些。
     */
    @Transaction
    @Query(
        """
        SELECT w.*, p.word_id AS p_word_id, p.state AS p_state,
               p.repetitions AS p_repetitions, p.interval_days AS p_interval_days,
               p.ease_factor AS p_ease_factor, p.next_review_at AS p_next_review_at,
               p.last_review_at AS p_last_review_at,
               p.correct_count AS p_correct_count, p.wrong_count AS p_wrong_count,
               p.review_count AS p_review_count,
               p.last_wrong_at AS p_last_wrong_at, p.is_in_wrong_book AS p_is_in_wrong_book
        FROM word w
        INNER JOIN word_progress p ON w.id = p.word_id
        WHERE p.is_in_wrong_book = 1
        ORDER BY p.last_wrong_at DESC
        LIMIT :limit
        """
    )
    suspend fun getWrongBookWords(limit: Int = 100): List<WordWithProgress>

    /** 按下标 / id 列表取词，保留传入顺序 */
    @Transaction
    @Query(
        """
        SELECT w.*, p.word_id AS p_word_id, p.state AS p_state,
               p.repetitions AS p_repetitions, p.interval_days AS p_interval_days,
               p.ease_factor AS p_ease_factor, p.next_review_at AS p_next_review_at,
               p.last_review_at AS p_last_review_at,
               p.correct_count AS p_correct_count, p.wrong_count AS p_wrong_count,
               p.review_count AS p_review_count,
               p.last_wrong_at AS p_last_wrong_at, p.is_in_wrong_book AS p_is_in_wrong_book
        FROM word w
        LEFT JOIN word_progress p ON w.id = p.word_id
        WHERE w.id IN (:wordIds)
        """
    )
    suspend fun getWordsByIds(wordIds: List<Long>): List<WordWithProgress>

    /** 手动把单词移出错题本 */
    @Query("UPDATE word_progress SET is_in_wrong_book = 0 WHERE word_id = :wordId")
    suspend fun removeFromWrongBook(wordId: Long)

    /**
     * 手动把单词加进错题本。
     *
     * 与答错自动收录的区别：不改动 [WordProgress.wrongCount]。
     * wrongCount 是**正确率的分母**，用户手动收录一个还没做过题的词时
     * 并没有答错，把它计成错误会让统计失真。
     *
     * [lastWrongAt] 只在原本为 0 时补上当前时间，
     * 让手动收录的词也能排进「最近答错」的顺序里。
     */
    @Transaction
    suspend fun addToWrongBook(wordId: Long, now: Long = System.currentTimeMillis()) {
        val existing = getProgress(wordId)
        if (existing == null) {
            // 这个词从未学过，没有进度行可更新 —— 先造一条
            upsertProgress(
                WordProgress(
                    wordId = wordId,
                    state = WordState.NEW,
                    lastWrongAt = now,
                    isInWrongBook = true
                )
            )
        } else {
            upsertProgress(
                existing.copy(
                    isInWrongBook = true,
                    lastWrongAt = if (existing.lastWrongAt == 0L) now else existing.lastWrongAt
                )
            )
        }
    }

    /** 批量收进错题本，用于多选操作 */
    @Transaction
    suspend fun addAllToWrongBook(wordIds: List<Long>, now: Long = System.currentTimeMillis()) {
        wordIds.forEach { addToWrongBook(it, now) }
    }

    /** 批量移出错题本 */
    @Transaction
    suspend fun removeAllFromWrongBook(wordIds: List<Long>) {
        wordIds.forEach { removeFromWrongBook(it) }
    }

    /** 清空错题本，返回受影响行数 */
    @Query("UPDATE word_progress SET is_in_wrong_book = 0 WHERE is_in_wrong_book = 1")
    suspend fun clearWrongBook(): Int

    // ============================================================
    // 作答记录
    // ============================================================

    @Insert
    suspend fun insertRecord(record: StudyRecord): Long

    @Insert
    suspend fun insertRecords(records: List<StudyRecord>)

    @Query(
        """
        SELECT r.* FROM study_record r
        INNER JOIN word w ON w.id = r.word_id
        WHERE w.book_id = :bookId
        ORDER BY r.timestamp DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentRecords(bookId: Long, limit: Int): List<StudyRecord>

    @Query("SELECT COUNT(*) FROM study_record WHERE study_date = :date")
    suspend fun countRecordsOn(date: String): Int

    /** 清理早于指定时间的历史明细，防止表无限增长 */
    @Query("DELETE FROM study_record WHERE timestamp < :before")
    suspend fun deleteRecordsBefore(before: Long): Int

    @Query("DELETE FROM study_record")
    suspend fun clearAllRecords()
}

/** 状态计数投影 */
data class StateCount(
    val state: Int,
    val count: Int
)

/** 单日复习量投影 */
data class DayLoad(
    val dayStart: Long,
    val count: Int
)
