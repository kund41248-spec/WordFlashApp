package com.wordflash.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wordflash.app.data.local.dao.MarkDao
import com.wordflash.app.data.local.dao.ProgressDao
import com.wordflash.app.data.local.dao.StatsDao
import com.wordflash.app.data.local.dao.WordBookDao
import com.wordflash.app.data.local.entity.DailyStats
import com.wordflash.app.data.local.entity.StudyRecord
import com.wordflash.app.data.local.entity.Word
import com.wordflash.app.data.local.entity.WordBook
import com.wordflash.app.data.local.entity.WordMark
import com.wordflash.app.data.local.entity.WordProgress

/**
 * 应用数据库。
 *
 * 版本策略：**每次修改实体结构都必须 +1 并提供 Migration**。
 * 开发期若数据可丢，可临时用 fallbackToDestructiveMigration()，
 * 但**上线版本绝不能保留** —— 会把用户的学习记录全部清掉。
 */
@Database(
    entities = [
        WordBook::class,
        Word::class,
        WordProgress::class,
        StudyRecord::class,
        DailyStats::class,
        WordMark::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun wordBookDao(): WordBookDao
    abstract fun progressDao(): ProgressDao
    abstract fun statsDao(): StatsDao
    abstract fun markDao(): MarkDao

    companion object {
        const val DB_NAME = "word_flash.db"

        /**
         * v1 → v2：新增错题本支持。
         *
         * `word_progress` 增加两个字段：
         * - `last_wrong_at`     最近答错时间，用于错题本排序
         * - `is_in_wrong_book`  是否在错题本中（可被「答对」清除）
         *
         * 并为其建索引。两个字段都有默认值，已有数据自动填充，
         * 老用户的学习进度不会丢失。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE word_progress " +
                        "ADD COLUMN last_wrong_at INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE word_progress " +
                        "ADD COLUMN is_in_wrong_book INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_word_progress_is_in_wrong_book " +
                        "ON word_progress (is_in_wrong_book)"
                )

                // 把历史上答错过的词补进错题本，让老用户升级后错题本不是空的。
                // 旧版没记录「何时答错」，用 last_review_at 近似。
                db.execSQL(
                    "UPDATE word_progress " +
                        "SET is_in_wrong_book = 1, last_wrong_at = last_review_at " +
                        "WHERE wrong_count > 0"
                )
            }
        }

        /**
         * v2 → v3：错题本支持「错因」与「自定义标签」。
         *
         * `word_mark` 增加两个字段：
         * - `wrong_reason`  自由文本错因
         * - `wrong_tags`    自定义标签，多个用分号分隔
         *
         * 两者默认空串，老用户的笔记、收藏、生词本标记全部原样保留。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE word_mark " +
                        "ADD COLUMN wrong_reason TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE word_mark " +
                        "ADD COLUMN wrong_tags TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * 数据库结构变更登记表。
         * 每次修改实体结构都要：version +1 并在此追加一个 Migration。
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
