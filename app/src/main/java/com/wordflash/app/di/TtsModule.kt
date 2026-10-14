package com.wordflash.app.di

import android.content.Context
import com.wordflash.app.tts.TtsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * TTS 依赖注入。
 *
 * 做成 @Singleton：TextToSpeech 初始化是异步的（几百毫秒），
 * 且持有系统服务连接。全局一个实例可以避免每次进入学习页都重新初始化。
 */
@Module
@InstallIn(SingletonComponent::class)
object TtsModule {

    @Provides
    @Singleton
    fun provideTtsManager(
        @ApplicationContext context: Context
    ): TtsManager = TtsManager(context)
}
