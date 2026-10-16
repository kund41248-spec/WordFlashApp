package com.wordflash.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单词标记：收藏 + 生词本。
 *
 * 拆成独立表而不是塞进 [WordProgress]，原因有两点：
 * 1. 语义不同 —— 进度是「算法算出来的」，标记是「用户手动打的」
 * 2. 用户可能标记一个还没学过的词，此时不应凭空造出一条进度记录
 *
 * 只有被标记过的词才在这张表里有行，查询生词本时 JOIN word 即可。
 */
@Entity(
    tableName = "word_mark",
    foreignKeys = [
        ForeignKey(
            entity = Word::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["is_favorite"]),
        Index(value = ["is_in_wordbook"])
    ]
)
data class WordMark(
    @PrimaryKey
    @ColumnInfo(name = "word_id")
    val wordId: Long,

    /** 是否收藏 */
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    /** 是否加入生词本（答错时自动加入） */
    @ColumnInfo(name = "is_in_wordbook")
    val isInWordbook: Boolean = false,

    /** 用户为该词写的笔记 */
    @ColumnInfo(name = "note")
    val note: String = "",

    /**
     * 错因，自由文本。如「拼写记错」「和 adapt 混了」。
     *
     * 只对错题本里的词有意义。用自由文本而不是预设枚举，
     * 因为每个人记错的原因千奇百怪，预设选项永远不够用；
     * 需要归类时靠 [wrongTags] 解决。
     */
    @ColumnInfo(name = "wrong_reason")
    val wrongReason: String = "",

    /**
     * 自定义标签，多个用「;」分隔，如 `形近词;高频`。
     *
     * 存成一个字符串而不是建关联表，是权衡后的选择：
     * 标签只用于筛选和展示，没有「重命名标签」这类需要一致性的操作，
     * 为它多两张表和一次 JOIN 不划算。分隔符选分号是因为
     * 用户给标签起名时几乎不会用到它。
     */
    @ColumnInfo(name = "wrong_tags")
    val wrongTags: String = "",

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** 两个标记都没打、也没写任何内容时，这行记录可以安全删除，避免表膨胀 */
    val isEmpty: Boolean
        get() = !isFavorite && !isInWordbook &&
            note.isBlank() && wrongReason.isBlank() && wrongTags.isBlank()

    /** 标签列表，已去掉空项 */
    val tagList: List<String>
        get() = wrongTags.split(TAG_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val TAG_SEPARATOR = ";"

        /** 把标签列表拼回存储格式，顺带去重和去空 */
        fun joinTags(tags: List<String>): String =
            tags.map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(TAG_SEPARATOR.toString())
    }
}
