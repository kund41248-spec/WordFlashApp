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

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** 两个标记都没打时，这行记录可以安全删除，避免表膨胀 */
    val isEmpty: Boolean
        get() = !isFavorite && !isInWordbook && note.isBlank()
}
