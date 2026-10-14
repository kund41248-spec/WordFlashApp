package com.wordflash.app.data.seed

import com.wordflash.app.data.local.entity.Word

/**
 * 词库 JSON 的序列化模型。
 *
 * 文件放在 `assets/dict/` 下，格式如下：
 * ```json
 * {
 *   "name": "四级核心词汇",
 *   "description": "大学英语四级高频词",
 *   "category": "CET4",
 *   "version": 1,
 *   "words": [
 *     {
 *       "spelling": "abandon",
 *       "phonetic": "/əˈbændən/",
 *       "pos": "v.",
 *       "meaning": "放弃；抛弃",
 *       "example": "He abandoned his car in the snow.",
 *       "exampleTranslation": "他把车丢弃在雪地里。",
 *       "difficulty": 2
 *     }
 *   ]
 * }
 * ```
 *
 * 之所以定义中间的 DTO 而不是直接用 Room 实体反序列化，
 * 是因为 JSON 里不应该出现 bookId 这类数据库外键 —— 它由导入流程注入。
 */
data class DictPackDto(
    val name: String,
    val description: String = "",
    val category: String,
    val version: Int = 1,
    val words: List<DictWordDto> = emptyList()
)

data class DictWordDto(
    val spelling: String,
    val phonetic: String = "",
    val pos: String = "",
    val meaning: String,
    val example: String = "",
    val exampleTranslation: String = "",
    val difficulty: Int = 3
) {
    /** 转成 Room 实体。字段名做了简写映射，让 JSON 更紧凑。 */
    fun toEntity(bookId: Long): Word = Word(
        bookId = bookId,
        spelling = spelling.trim().lowercase(),
        phonetic = phonetic,
        meaning = meaning,
        partOfSpeech = pos,
        example = example,
        exampleTranslation = exampleTranslation,
        difficulty = difficulty.coerceIn(1, 5)
    )
}
