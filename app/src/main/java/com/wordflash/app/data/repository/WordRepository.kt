package com.wordflash.app.data.repository

import com.wordflash.app.data.local.dao.WordBookDao
import com.wordflash.app.data.local.entity.BookCategory
import com.wordflash.app.data.local.entity.Word
import com.wordflash.app.data.local.entity.WordBook
import com.wordflash.app.data.seed.DictPackDto
import com.wordflash.app.data.seed.DictWordDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 词库与单词的仓库层。UI 只依赖这一层，不直接接触 DAO。
 */
@Singleton
class WordRepository @Inject constructor(
    private val wordBookDao: WordBookDao
) {

    // ============================================================
    // 查询
    // ============================================================

    fun observeAllBooks(): Flow<List<WordBook>> = wordBookDao.observeAllBooks()

    fun observeBook(bookId: Long): Flow<WordBook?> = wordBookDao.observeBook(bookId)

    fun observeWordsInBook(bookId: Long): Flow<List<Word>> =
        wordBookDao.observeWordsInBook(bookId)

    suspend fun getBook(bookId: Long): WordBook? = wordBookDao.getBook(bookId)

    /** 一次性获取全部词库，用于首页概览等非响应式场景 */
    suspend fun getAllBooksOnce(): List<WordBook> = wordBookDao.getAllBooks()

    suspend fun getWord(wordId: Long): Word? = wordBookDao.getWord(wordId)

    suspend fun search(keyword: String): List<Word> {
        val k = keyword.trim()
        if (k.isEmpty()) return emptyList()
        return wordBookDao.searchWords(k)
    }

    suspend fun countWords(bookId: Long): Int = wordBookDao.countWordsInBook(bookId)

    /** 为选择题挑选干扰项 */
    suspend fun getDistractors(bookId: Long, excludeWordId: Long, count: Int): List<Word> =
        wordBookDao.getRandomDistractors(bookId, excludeWordId, count)

    // ============================================================
    // 导入
    // ============================================================

    /**
     * 把解析好的词库写入数据库。
     *
     * @return 实际写入的词条数（重复的拼写会被忽略）
     */
    suspend fun importPack(pack: DictPackDto, bookNameOverride: String? = null): ImportOutcome {
        val name = bookNameOverride?.takeIf { it.isNotBlank() } ?: pack.name

        // 同名自定义词库已存在时，创建一个带序号的新词库，避免混淆
        val finalName = uniqueBookName(name)

        val bookId = wordBookDao.insertBook(
            WordBook(
                name = finalName,
                description = pack.description.ifBlank { "用户导入" },
                category = BookCategory.CUSTOM,
                isBuiltIn = false,
                // 自定义词库排在内置词库之后
                sortOrder = 1000 + (wordBookDao.countBooks())
            )
        )

        var inserted = 0
        var duplicated = 0
        pack.words.chunked(200).forEach { chunk ->
            val entities = chunk.map { it.toEntity(bookId) }
            val ids = wordBookDao.insertWords(entities)
            inserted += ids.size
            duplicated += entities.size - ids.size
        }

        wordBookDao.updateWordCount(bookId, inserted)

        // 一条都没插进去说明整包重复，删掉刚建的空词库
        if (inserted == 0) {
            wordBookDao.deleteCustomBook(bookId)
            return ImportOutcome(bookId = 0, inserted = 0, duplicated = duplicated, isEmpty = true)
        }

        return ImportOutcome(
            bookId = bookId,
            inserted = inserted,
            duplicated = duplicated,
            isEmpty = false
        )
    }

    /**
     * 向**已有**词库追加单词（内置词库也可以）。
     *
     * 与 [importPack] 的区别：不新建词库，直接写进 [bookId]。
     * 这一步是「往四级核心词汇里加一个 apple」能成立的关键 ——
     * 内置词库同样允许追加，因为用户本来就该能补自己缺的词。
     *
     * 拼写冲突按 IGNORE 处理：已有的词保留原样（连同它的学习进度），
     * 不覆盖、不报错，只把 [duplicated] 计数返回给 UI 提示用户。
     *
     * @return [ImportOutcome]，其中 [ImportOutcome.bookId] 即传入的 [bookId]；
     *   发现词库不存在时返回 `null`。
     */
    suspend fun appendWords(
        bookId: Long,
        words: List<DictWordDto>
    ): ImportOutcome? {
        // 词库可能已被删除（用户停在录入页时在别处删了），先确认存在
        wordBookDao.getBook(bookId) ?: return null

        val valid = words
            .map { it.copy(spelling = it.spelling.trim().lowercase(), meaning = it.meaning.trim()) }
            .filter { it.spelling.isNotEmpty() && it.meaning.isNotEmpty() }

        if (valid.isEmpty()) {
            return ImportOutcome(
                bookId = bookId,
                inserted = 0,
                duplicated = 0,
                isEmpty = true
            )
        }

        var inserted = 0
        var duplicated = 0
        valid.chunked(200).forEach { chunk ->
            val entities = chunk.map { it.toEntity(bookId) }
            val ids = wordBookDao.insertWords(entities)
            inserted += ids.size
            duplicated += entities.size - ids.size
        }

        // wordCount 是冗余字段，必须跟着实际词数走；
        // 用 refreshWordCount 重新 COUNT 而不是累加，
        // 这样即使中途有并发写入也不会算错。
        if (inserted > 0) {
            wordBookDao.refreshWordCount(bookId)
        }

        return ImportOutcome(
            bookId = bookId,
            inserted = inserted,
            duplicated = duplicated,
            // 一个都没插进去，说明整批都已在词库里
            isEmpty = inserted == 0
        )
    }

    private suspend fun uniqueBookName(base: String): String {
        val existing = wordBookDao.getBookByCategory(BookCategory.CUSTOM)
        // 简易去重：查全部词库名太绕，这里只在明显同名时加时间戳后缀
        if (existing?.name != base) return base
        return "$base (${System.currentTimeMillis() % 100000})"
    }

    /** 删除自定义词库。内置词库不可删，返回 false。 */
    suspend fun deleteCustomBook(bookId: Long): Boolean =
        wordBookDao.deleteCustomBook(bookId) > 0

    data class ImportOutcome(
        val bookId: Long,
        val inserted: Int,
        val duplicated: Int,
        val isEmpty: Boolean
    )
}
