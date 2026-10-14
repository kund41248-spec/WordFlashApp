package com.wordflash.app.data.repository

import com.wordflash.app.data.local.dao.MarkDao
import com.wordflash.app.data.local.dao.ProgressDao
import com.wordflash.app.data.local.entity.Word
import com.wordflash.app.data.local.entity.WordMark
import com.wordflash.app.data.local.entity.WordProgress
import com.wordflash.app.data.local.entity.WordState
import com.wordflash.app.domain.model.WordWithProgress
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学习会话所需的数据访问：待学队列、进度、标记、错题本。
 *
 * **注意**：这里只负责「存取」，不含调度算法。
 * 什么时候该复习、间隔多久，全部由
 * [com.wordflash.app.domain.scheduler.EbbinghausScheduler] 决定。
 */
@Singleton
class StudyRepository @Inject constructor(
    private val progressDao: ProgressDao,
    private val markDao: MarkDao
) {

    // ============================================================
    // 学习队列
    // ============================================================

    /**
     * 按词库组装学习列表：先取到期复习的词，再补新词。
     *
     * @param newWordLimit 本次最多引入多少个新词。0 表示只复习不学新词。
     */
    suspend fun buildSession(
        bookId: Long,
        now: Long,
        reviewLimit: Int,
        newWordLimit: Int
    ): List<WordWithProgress> {
        val due = progressDao.getDueWords(bookId, now, reviewLimit)
        if (newWordLimit <= 0) return due

        // 复习配额与新词配额相互独立：即使复习积压，
        // 用户也应该能学到新词，否则永远在原地打转。
        val newWords = progressDao.getNewWords(bookId, newWordLimit)
        return due + newWords
    }

    /**
     * 按指定词表组装学习列表（用于「练习错题」）。
     *
     * 保持与传入顺序一致 —— 调用方通常已按「最近答错」排好序。
     */
    suspend fun buildSessionFromWords(
        wordIds: List<Long>,
        limit: Int
    ): List<WordWithProgress> {
        if (wordIds.isEmpty()) return emptyList()
        val picked = wordIds.take(limit)
        val found = progressDao.getWordsByIds(picked)
        // getWordsByIds 的 IN 查询不保证顺序，这里按传入顺序重排
        val byId = found.associateBy { it.word.id }
        return picked.mapNotNull { byId[it] }
    }

    fun observeDueCount(bookId: Long, now: Long): Flow<Int> =
        progressDao.observeDueCount(bookId, now)

    /** 一次性查询到期数量，用于词库列表批量刷新 */
    suspend fun countDueNow(bookId: Long, now: Long): Int =
        progressDao.countDueNow(bookId, now)

    fun observeTotalDueCount(now: Long): Flow<Int> =
        progressDao.observeTotalDueCount(now)

    suspend fun getStateCounts(bookId: Long) = progressDao.getStateCounts(bookId)

    suspend fun getUpcomingLoad(from: Long, to: Long) =
        progressDao.getUpcomingReviewLoad(from, to)

    fun observeMasteredCount(): Flow<Int> =
        progressDao.observeCountByState(WordState.MASTERED)

    // ============================================================
    // 进度
    // ============================================================

    suspend fun getProgress(wordId: Long): WordProgress? = progressDao.getProgress(wordId)

    suspend fun saveProgress(progress: WordProgress) =
        progressDao.upsertProgress(progress)

    // ============================================================
    // 错题本
    // ============================================================

    /** 错题本列表（含单词与进度），按最近答错时间倒序 */
    fun observeWrongBook(): Flow<List<WordWithProgress>> = progressDao.observeWrongBook()

    /** 错题本数量，用于 tab 角标 */
    fun observeWrongBookCount(): Flow<Int> = progressDao.observeWrongBookCount()

    /** 取错题本的词 id，用于组卷练习 */
    suspend fun getWrongBookWordIds(limit: Int = 100): List<Long> =
        progressDao.getWrongBookWords(limit).map { it.word.id }

    /** 手动移出错题本 */
    suspend fun removeFromWrongBook(wordId: Long) =
        progressDao.removeFromWrongBook(wordId)

    /** 清空错题本，返回移除数量 */
    suspend fun clearWrongBook(): Int = progressDao.clearWrongBook()

    // ============================================================
    // 标记（收藏 / 生词本）
    //
    // 注意分工：生词本是**用户手动**添加的，
    // 答错自动收录走的是错题本（见上面的「错题本」分区）。
    // ============================================================

    fun observeFavorites(): Flow<List<Word>> = markDao.observeFavorites()

    fun observeWordbook(): Flow<List<Word>> = markDao.observeWordbook()

    fun observeFavoriteCount(): Flow<Int> = markDao.observeFavoriteCount()

    fun observeWordbookCount(): Flow<Int> = markDao.observeWordbookCount()

    suspend fun getMark(wordId: Long): WordMark? = markDao.getMark(wordId)

    suspend fun toggleFavorite(wordId: Long): Boolean = markDao.toggleFavorite(wordId)

    suspend fun toggleWordbook(wordId: Long): Boolean = markDao.toggleWordbook(wordId)

    suspend fun saveNote(wordId: Long, note: String) {
        val existing = markDao.getMark(wordId)
        markDao.upsert(
            (existing ?: WordMark(wordId = wordId)).copy(
                note = note,
                updatedAt = System.currentTimeMillis()
            )
        )
        markDao.deleteIfEmpty(wordId)
    }
}
