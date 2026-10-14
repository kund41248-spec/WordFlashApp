package com.wordflash.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单词条目。
 *
 * 删除词库时级联删除其下所有单词（[ForeignKey.CASCADE]）；
 * 单词的拼写在同一词库内唯一，防止重复导入。
 */
@Entity(
    tableName = "word",
    foreignKeys = [
        ForeignKey(
            entity = WordBook::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"]),
        // 保证同一词库内拼写不重复（忽略大小写由写入时统一小写保证）
        Index(value = ["book_id", "spelling"], unique = true),
        // 听音辨词、搜索需要按拼写查找
        Index(value = ["spelling"])
    ]
)
data class Word(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "book_id")
    val bookId: Long,

    /** 单词拼写 */
    @ColumnInfo(name = "spelling")
    val spelling: String,

    /** 音标，如 /ˈæpl/ */
    @ColumnInfo(name = "phonetic")
    val phonetic: String = "",

    /** 中文释义，多义项用「；」分隔 */
    @ColumnInfo(name = "meaning")
    val meaning: String,

    /** 词性，如 n. / v. / adj. */
    @ColumnInfo(name = "part_of_speech")
    val partOfSpeech: String = "",

    /** 英文例句 */
    @ColumnInfo(name = "example")
    val example: String = "",

    /** 例句中文翻译 */
    @ColumnInfo(name = "example_translation")
    val exampleTranslation: String = "",

    /** 自定义音频地址；为空则使用 TTS 朗读 */
    @ColumnInfo(name = "audio_url")
    val audioUrl: String = "",

    /** 难度标记 1~5，用于选词填空的干扰项挑选 */
    @ColumnInfo(name = "difficulty")
    val difficulty: Int = 3
)
