package com.wordflash.app.di

import android.content.Context
import androidx.room.Room
import com.wordflash.app.data.local.AppDatabase
import com.wordflash.app.data.local.dao.MarkDao
import com.wordflash.app.data.local.dao.ProgressDao
import com.wordflash.app.data.local.dao.StatsDao
import com.wordflash.app.data.local.dao.WordBookDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库依赖注入。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room
        .databaseBuilder(context, AppDatabase::class.java, AppDatabase.DB_NAME)
        .addMigrations(*AppDatabase.MIGRATIONS)
        // 词库播种会一次性插入几千条，开启 WAL 提升并发读性能；
        // 外键约束必须显式开启，否则 @ForeignKey 只写不生效。
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()

    @Provides
    fun provideWordBookDao(db: AppDatabase): WordBookDao = db.wordBookDao()

    @Provides
    fun provideProgressDao(db: AppDatabase): ProgressDao = db.progressDao()

    @Provides
    fun provideStatsDao(db: AppDatabase): StatsDao = db.statsDao()

    @Provides
    fun provideMarkDao(db: AppDatabase): MarkDao = db.markDao()
}
