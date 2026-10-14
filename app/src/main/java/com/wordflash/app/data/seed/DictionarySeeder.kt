package com.wordflash.app.data.seed

import android.content.Context
import android.util.Log
import com.wordflash.app.data.local.dao.WordBookDao
import com.wordflash.app.data.local.entity.BookCategory
import com.wordflash.app.data.local.entity.WordBook
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内置词库播种器。
 *
 * 首次启动时把 assets/dict 目录下的 JSON 词库写入数据库。判断依据是
 * **内置词库的条数**而不是「数据库是否为空」—— 这样即使用户
 * 已经导入了自己的词库，内置词库依然能被补齐（例如升级新增了词库）。
 *
 * 播种是幂等的：已存在的词库（按 category 判断）会被跳过。
 */
@Singleton
class DictionarySeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wordBookDao: WordBookDao
) {

    companion object {
        private const val TAG = "DictionarySeeder"
        private const val DICT_DIR = "dict"

        /**
         * 已被移除的内置词库分类。
         *
         * 用户反馈高考和雅思词库用不上，已从 assets 中删除。
         * 但**老用户数据库里可能已经有这两个词库**（旧版本播种进去的），
         * 所以每次启动都要顺手清理一次 —— 否则它们会永远留在词库列表里，
         * 而内置词库又是禁止用户手动删除的。
         *
         * 这里用「分类」而不是「名称」匹配：名称是展示用的，随时可能改。
         */
        private val REMOVED_CATEGORIES = listOf(BookCategory.GAOKAO, BookCategory.IELTS)
    }

    /**
     * 执行播种。返回本次新导入的词条总数。
     * 应在应用启动时调用一次（协程中），失败不应阻塞 UI。
     */
    suspend fun seedIfNeeded(): Int = withContext(Dispatchers.IO) {
        // 先清理已下线的内置词库
        purgeRemovedBooks()

        val files = runCatching {
            context.assets.list(DICT_DIR)?.filter { it.endsWith(".json") } ?: emptyList()
        }.getOrElse {
            Log.w(TAG, "读取 assets/$DICT_DIR 失败", it)
            return@withContext 0
        }

        if (files.isEmpty()) {
            Log.w(TAG, "assets/$DICT_DIR 下没有找到词库文件")
            return@withContext 0
        }

        var inserted = 0
        files.forEach { fileName ->
            inserted += runCatching { seedOneFile(fileName) }
                .onFailure { Log.e(TAG, "导入 $fileName 失败", it) }
                .getOrDefault(0)
        }

        Log.i(TAG, "播种完成，新增 $inserted 条")
        inserted
    }

    /**
     * 删除已下线的内置词库（高考、雅思）。
     *
     * 级联删除会一并清掉这些词库下的单词、学习进度与统计明细 ——
     * 这正是期望的行为：词库都没了，留着孤立的进度记录没有意义，
     * 反而会污染「累计学习词次」等统计。
     */
    private suspend fun purgeRemovedBooks() {
        REMOVED_CATEGORIES.forEach { category ->
            runCatching {
                val book = wordBookDao.getBookByCategory(category) ?: return@forEach
                if (!book.isBuiltIn) return@forEach   // 用户自建的同类词库不动
                val removed = wordBookDao.deleteBookById(book.id)
                if (removed > 0) {
                    Log.i(TAG, "已移除下线的内置词库：${book.name}")
                }
            }.onFailure { Log.w(TAG, "清理 $category 失败", it) }
        }
    }

    private suspend fun seedOneFile(fileName: String): Int {
        val json = context.assets.open("$DICT_DIR/$fileName")
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        val dto = parsePack(json)

        // 已存在同 category 的内置词库则跳过，保证幂等
        val existing = wordBookDao.getBookByCategory(dto.category)
        if (existing != null && existing.isBuiltIn) {
            Log.d(TAG, "${dto.category} 已存在，跳过")
            return 0
        }

        val sortOrder = runCatching { readSortOrder(json) }.getOrDefault(0)

        val bookId = wordBookDao.insertBook(
            WordBook(
                name = dto.name,
                description = dto.description,
                category = dto.category,
                wordCount = dto.words.size,
                isBuiltIn = true,
                sortOrder = sortOrder
            )
        )

        // 分批插入：SQLite 单条语句的绑定参数有上限（默认 999），
        // 每批 200 条 × 9 个字段 = 1800，仍在安全范围外，
        // 因此这里按「条数」分批更稳妥。
        var count = 0
        dto.words.chunked(200).forEach { chunk ->
            val entities = chunk.map { it.toEntity(bookId) }
            val ids = wordBookDao.insertWords(entities)
            count += ids.size
        }

        wordBookDao.updateWordCount(bookId, count)
        return count
    }

    /** 解析词库 JSON。手写解析而不引入 Gson/Moshi，避免为一个功能加依赖。 */
    private fun parsePack(json: String): DictPackDto {
        val root = JSONObject(json)
        val wordsArray: JSONArray = root.optJSONArray("words") ?: JSONArray()
        val words = ArrayList<DictWordDto>(wordsArray.length())
        for (i in 0 until wordsArray.length()) {
            val o = wordsArray.getJSONObject(i)
            val spelling = o.optString("spelling").trim()
            val meaning = o.optString("meaning").trim()
            // 拼写或释义为空视为脏数据，跳过 —— 否则会占用唯一索引且无法学习
            if (spelling.isEmpty() || meaning.isEmpty()) continue
            words += DictWordDto(
                spelling = spelling,
                phonetic = o.optString("phonetic"),
                pos = o.optString("pos"),
                meaning = meaning,
                example = o.optString("example"),
                exampleTranslation = o.optString("exampleTranslation"),
                difficulty = o.optInt("difficulty", 3)
            )
        }
        return DictPackDto(
            name = root.optString("name", "未命名词库"),
            description = root.optString("description"),
            category = root.optString("category", BookCategory.CUSTOM),
            version = root.optInt("version", 1),
            words = words
        )
    }

    private fun readSortOrder(json: String): Int =
        JSONObject(json).optInt("sortOrder", 0)
}
