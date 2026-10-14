package com.wordflash.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 词库分类。用常量而非 enum，便于以后扩展而不动数据库。
 */
object BookCategory {
    const val CET4 = "CET4"        // 四级
    const val CET6 = "CET6"        // 六级
    const val KAOYAN = "KAOYAN"    // 考研
    const val GAOKAO = "GAOKAO"    // 高考
    const val IELTS = "IELTS"      // 雅思
    const val TOEFL = "TOEFL"      // 托福
    const val CUSTOM = "CUSTOM"    // 用户导入
}

/**
 * 词库。
 *
 * [isBuiltIn] 为 true 表示随 APP 预置的词库，用户不可删除（但可以停止学习）；
 * 用户导入的词库为 false，可自由删除。
 */
@Entity(
    tableName = "word_book",
    indices = [Index(value = ["category"])]
)
data class WordBook(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 词库名称，如「四级核心词汇」 */
    @ColumnInfo(name = "name")
    val name: String,

    /** 一句话描述，显示在词库卡片上 */
    @ColumnInfo(name = "description")
    val description: String = "",

    /** 见 [BookCategory] */
    @ColumnInfo(name = "category")
    val category: String = BookCategory.CUSTOM,

    /** 单词总数，冗余存储避免每次 COUNT 查询 */
    @ColumnInfo(name = "word_count")
    val wordCount: Int = 0,

    /** 是否内置词库 */
    @ColumnInfo(name = "is_built_in")
    val isBuiltIn: Boolean = false,

    /** 封面用的强调色（ARGB），0 表示使用主题默认色 */
    @ColumnInfo(name = "accent_color")
    val accentColor: Long = 0L,

    /** 排序权重，越小越靠前 */
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
