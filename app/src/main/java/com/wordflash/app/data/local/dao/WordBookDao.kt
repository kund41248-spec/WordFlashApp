package com.wordflash.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wordflash.app.data.local.entity.Word
import com.wordflash.app.data.local.entity.WordBook
import kotlinx.coroutines.flow.Flow

/**
 * 词库与单词的数据访问。
 */
@Dao
interface WordBookDao {

    // ============================================================
    // 词库
    // ============================================================

    /** 全部词库，按排序权重与创建时间排列 */
    @Query("SELECT * FROM word_book ORDER BY sort_order ASC, created_at ASC")
    fun observeAllBooks(): Flow<List<WordBook>>

    /** 一次性查询全部词库，用于首页概览等非响应式场景 */
    @Query("SELECT * FROM word_book ORDER BY sort_order ASC, created_at ASC")
    suspend fun getAllBooks(): List<WordBook>

    /** 内置词库 */
    @Query("SELECT * FROM word_book WHERE is_built_in = 1 ORDER BY sort_order ASC")
    suspend fun getBuiltInBooks(): List<WordBook>

    @Query("SELECT * FROM word_book WHERE id = :bookId")
    fun observeBook(bookId: Long): Flow<WordBook?>

    @Query("SELECT * FROM word_book WHERE id = :bookId")
    suspend fun getBook(bookId: Long): WordBook?

    @Query("SELECT * FROM word_book WHERE category = :category LIMIT 1")
    suspend fun getBookByCategory(category: String): WordBook?

    @Query("SELECT COUNT(*) FROM word_book")
    suspend fun countBooks(): Int

    @Query("SELECT COUNT(*) FROM word_book WHERE is_built_in = 1")
    suspend fun countBuiltInBooks(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: WordBook): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<WordBook>): List<Long>

    @Update
    suspend fun updateBook(book: WordBook)

    /** 仅可删除非内置词库，内置词库由 UI 层拦截 */
    @Query("DELETE FROM word_book WHERE id = :bookId AND is_built_in = 0")
    suspend fun deleteCustomBook(bookId: Long): Int

    /**
     * 按 id 删除词库，**不限制是否内置**。
     *
     * 仅供 [com.wordflash.app.data.seed.DictionarySeeder] 清理已下线的
     * 内置词库使用（如高考、雅思）。UI 层请用 [deleteCustomBook]。
     */
    @Query("DELETE FROM word_book WHERE id = :bookId")
    suspend fun deleteBookById(bookId: Long): Int

    @Query("UPDATE word_book SET word_count = :count WHERE id = :bookId")
    suspend fun updateWordCount(bookId: Long, count: Int)

    /** 重新统计某词库的单词数并写回 */
    @Transaction
    suspend fun refreshWordCount(bookId: Long) {
        updateWordCount(bookId, countWordsInBook(bookId))
    }

    // ============================================================
    // 单词
    // ============================================================

    @Query("SELECT * FROM word WHERE book_id = :bookId ORDER BY id ASC")
    fun observeWordsInBook(bookId: Long): Flow<List<Word>>

    @Query("SELECT * FROM word WHERE book_id = :bookId ORDER BY id ASC")
    suspend fun getWordsInBook(bookId: Long): List<Word>

    @Query("SELECT COUNT(*) FROM word WHERE book_id = :bookId")
    suspend fun countWordsInBook(bookId: Long): Int

    @Query("SELECT * FROM word WHERE id = :wordId")
    suspend fun getWord(wordId: Long): Word?

    @Query("SELECT * FROM word WHERE id IN (:ids)")
    suspend fun getWordsByIds(ids: List<Long>): List<Word>

    /**
     * 模糊搜索：拼写或释义命中即返回。
     * 限制条数避免输入单个字符时全表扫描返回过多结果。
     */
    @Query(
        """
        SELECT * FROM word
        WHERE spelling LIKE '%' || :keyword || '%'
           OR meaning LIKE '%' || :keyword || '%'
        ORDER BY
            CASE WHEN spelling LIKE :keyword || '%' THEN 0 ELSE 1 END,
            LENGTH(spelling) ASC
        LIMIT :limit
        """
    )
    suspend fun searchWords(keyword: String, limit: Int = 50): List<Word>

    /**
     * 批量插入单词。冲突时忽略 —— 配合唯一索引实现「重复导入不报错、也不覆盖已有数据」。
     * 返回成功插入的行 id（被忽略的不在返回列表中）。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWords(words: List<Word>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: Word): Long

    @Delete
    suspend fun deleteWord(word: Word)

    /**
     * 为选词填空/听音辨词挑选干扰项：从同一词库随机取若干个词。
     * 排除答案本身，避免出现「两个正确答案」。
     */
    @Query(
        """
        SELECT * FROM word
        WHERE book_id = :bookId AND id != :excludeWordId
        ORDER BY RANDOM()
        LIMIT :limit
        """
    )
    suspend fun getRandomDistractors(
        bookId: Long,
        excludeWordId: Long,
        limit: Int
    ): List<Word>
}
