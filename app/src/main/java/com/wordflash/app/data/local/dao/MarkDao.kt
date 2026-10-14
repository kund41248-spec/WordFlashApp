package com.wordflash.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wordflash.app.data.local.entity.Word
import com.wordflash.app.data.local.entity.WordMark
import kotlinx.coroutines.flow.Flow

/**
 * 收藏与生词本标记。
 */
@Dao
interface MarkDao {

    @Query("SELECT * FROM word_mark WHERE word_id = :wordId")
    suspend fun getMark(wordId: Long): WordMark?

    @Query("SELECT * FROM word_mark WHERE word_id = :wordId")
    fun observeMark(wordId: Long): Flow<WordMark?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mark: WordMark)

    /**
     * 标记清空时删除整行，保持表精简。
     * 只删「既没收藏、也没进生词本、也没笔记」的行。
     */
    @Query(
        """
        DELETE FROM word_mark
        WHERE word_id = :wordId
          AND is_favorite = 0
          AND is_in_wordbook = 0
          AND note = ''
        """
    )
    suspend fun deleteIfEmpty(wordId: Long)

    // ============================================================
    // 收藏列表
    // ============================================================

    @Transaction
    @Query(
        """
        SELECT w.* FROM word w
        INNER JOIN word_mark m ON m.word_id = w.id
        WHERE m.is_favorite = 1
        ORDER BY m.updated_at DESC
        """
    )
    fun observeFavorites(): Flow<List<Word>>

    // ============================================================
    // 生词本
    // ============================================================

    @Transaction
    @Query(
        """
        SELECT w.* FROM word w
        INNER JOIN word_mark m ON m.word_id = w.id
        WHERE m.is_in_wordbook = 1
        ORDER BY m.updated_at DESC
        """
    )
    fun observeWordbook(): Flow<List<Word>>

    @Query(
        """
        SELECT w.* FROM word w
        INNER JOIN word_mark m ON m.word_id = w.id
        WHERE m.is_in_wordbook = 1 AND w.book_id = :bookId
        ORDER BY m.updated_at DESC
        """
    )
    suspend fun getWordbookInBook(bookId: Long): List<Word>

    @Query("SELECT COUNT(*) FROM word_mark WHERE is_favorite = 1")
    fun observeFavoriteCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM word_mark WHERE is_in_wordbook = 1")
    fun observeWordbookCount(): Flow<Int>

    /** 答错时自动加入生词本，保留已有收藏状态 */
    @Transaction
    suspend fun addToWordbook(wordId: Long) {
        val existing = getMark(wordId)
        upsert(
            (existing ?: WordMark(wordId = wordId)).copy(
                isInWordbook = true,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 切换收藏，返回切换后的状态 */
    @Transaction
    suspend fun toggleFavorite(wordId: Long): Boolean {
        val existing = getMark(wordId)
        val next = !(existing?.isFavorite ?: false)
        upsert(
            (existing ?: WordMark(wordId = wordId)).copy(
                isFavorite = next,
                updatedAt = System.currentTimeMillis()
            )
        )
        deleteIfEmpty(wordId)
        return next
    }

    /** 切换生词本，返回切换后的状态 */
    @Transaction
    suspend fun toggleWordbook(wordId: Long): Boolean {
        val existing = getMark(wordId)
        val next = !(existing?.isInWordbook ?: false)
        upsert(
            (existing ?: WordMark(wordId = wordId)).copy(
                isInWordbook = next,
                updatedAt = System.currentTimeMillis()
            )
        )
        deleteIfEmpty(wordId)
        return next
    }

    @Query("DELETE FROM word_mark")
    suspend fun clearAll()
}
