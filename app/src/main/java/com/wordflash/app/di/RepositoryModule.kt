package com.wordflash.app.di

import com.wordflash.app.data.local.dao.MarkDao
import com.wordflash.app.data.local.dao.ProgressDao
import com.wordflash.app.data.local.dao.StatsDao
import com.wordflash.app.data.local.dao.WordBookDao
import com.wordflash.app.data.repository.StatsRepository
import com.wordflash.app.data.repository.StudyRepository
import com.wordflash.app.data.repository.WordRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 仓库层依赖注入。
 *
 * 仓库本身已标注 @Inject constructor + @Singleton，Hilt 能自动构造。
 * 这里显式提供是为了**接口与实现解耦留出空间** —— 将来若要为测试
 * 替换假实现，只需改这一处。
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideWordRepository(wordBookDao: WordBookDao): WordRepository =
        WordRepository(wordBookDao)

    @Provides
    @Singleton
    fun provideStudyRepository(
        progressDao: ProgressDao,
        markDao: MarkDao
    ): StudyRepository = StudyRepository(progressDao, markDao)

    @Provides
    @Singleton
    fun provideStatsRepository(
        statsDao: StatsDao,
        progressDao: ProgressDao
    ): StatsRepository = StatsRepository(statsDao, progressDao)
}
