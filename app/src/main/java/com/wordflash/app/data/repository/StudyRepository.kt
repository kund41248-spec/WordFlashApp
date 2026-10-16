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

    /**
     * 手动把单词加进错题本。
     *
     * 用于「练习时答错自动收录」之外的两条路径：
     * 用户在单词列表里手动收录，以及从错题本页面的「添加单词」入口收录。
     */
    suspend fun addToWrongBook(wordId: Long) =
        progressDao.addToWrongBook(wordId)

    /** 批量收进错题本 */
    suspend fun addAllToWrongBook(wordIds: List<Long>) =
        progressDao.addAllToWrongBook(wordIds)

    /** 批量移出错题本 */
    suspend fun removeAllFromWrongBook(wordIds: List<Long>) =
        progressDao.removeAllFromWrongBook(wordIds)

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

    // ============================================================
    // 错因与标签（错题本用）
    // ============================================================

    /** 带错因/标签的标记行，key = wordId，供错题本列表附加显示 */
    fun observeMarksWithWrongMeta(): Flow<List<WordMark>> =
        markDao.observeMarksWithWrongMeta()

    /** 曾用过的标签，供筛选面板列出候选 */
    fun observeAllWrongTags(): Flow<List<String>> = markDao.observeAllWrongTags()

    /**
     * 保存错因。传空串表示清空。
     * 界面上的输入框每次失焦/确认都会调它。
     */
    suspend fun saveWrongReason(wordId: Long, reason: String) =
        markDao.saveWrongMeta(wordId = wordId, reason = reason)

    /** 保存标签。传列表，内部按分号拼好；传空列表即清空 */
    suspend fun saveWrongTags(wordId: Long, tags: List<String>) =
        markDao.saveWrongMeta(wordId = wordId, tags = WordMark.joinTags(tags))

    /**
     * 给单词追加一个标签（已存在则忽略）。
     *
     * 「标记薄弱」就是往标签里加一个约定名 —— 见 [TAG_WEAK]。
     * 之所以不给「掌握/薄弱」单独建字段：它本质就是一个分类标签，
     * 复用标签机制后，用户还能自己再加「易混」「考前必看」之类，
     * 不必每加一种分类就改一次数据库。
     */
    suspend fun addWrongTag(wordId: Long, tag: String) {
        val existing = markDao.getMark(wordId)
        val current = existing?.tagList ?: emptyList()
        if (current.any { it.equals(tag, ignoreCase = true) }) return
        markDao.saveWrongMeta(
            wordId = wordId,
            tags = WordMark.joinTags(current + tag)
        )
    }

    /** 移除某个标签 */
    suspend fun removeWrongTag(wordId: Long, tag: String) {
        val existing = markDao.getMark(wordId) ?: return
        markDao.saveWrongMeta(
            wordId = wordId,
            tags = WordMark.joinTags(
                existing.tagList.filterNot { it.equals(tag, ignoreCase = true) }
            )
        )
    }

    companion object {
        /** 「薄弱」标签 —— 用户主动标记需要重点攻克的词 */
        const val TAG_WEAK = "薄弱"

        /** 「已掌握」标签 —— 用户主动标记，用于和「移出错题本」区分开 */
        const val TAG_MASTERED = "已掌握"
    }
}
