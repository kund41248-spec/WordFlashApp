package com.wordflash.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wordflash.app.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** DataStore 实例挂到 Context 上，保证全局唯一 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "word_flash_settings"
)

/**
 * 用户设置仓库。
 *
 * ## 为什么用 DataStore 而不是 Room
 *
 * 设置是「少量键值对 + 高频读 + 低频写」，DataStore 正是为此设计的：
 * 无需建表、无迁移负担、天然异步。Room 适合结构化数据，
 * 拿它存十来个开关属于杀鸡用牛刀。
 *
 * ## 为什么不用 SharedPreferences
 *
 * SharedPreferences 在主线程做磁盘 IO 会阻塞，且没有 Flow 支持。
 * DataStore 基于协程 + Flow，Compose 可以直接 collect。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val AUTO_PLAY_AUDIO = booleanPreferencesKey("auto_play_audio")
        val REPEAT_WRONG = booleanPreferencesKey("repeat_wrong_at_end")
        val DAILY_NEW_LIMIT = intPreferencesKey("daily_new_limit")
        val DAILY_REVIEW_LIMIT = intPreferencesKey("daily_review_limit")
        /** 上次清理缓存的时间，用于设置页显示 */
        val LAST_CACHE_CLEAR = stringPreferencesKey("last_cache_clear")
    }

    // ============================================================
    // 主题
    // ============================================================

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE]
        // 用 runCatching 兜底：万一存了非法值（如旧版本遗留），
        // 回退到跟随系统而不是崩溃
        runCatching { ThemeMode.valueOf(raw ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map {
        it[Keys.DYNAMIC_COLOR] ?: false
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    // ============================================================
    // 发音
    // ============================================================

    /** 语速倍率，范围 0.5 ~ 1.5 */
    val speechRate: Flow<Float> = context.settingsDataStore.data.map {
        (it[Keys.SPEECH_RATE] ?: 1.0f).coerceIn(0.5f, 1.5f)
    }

    suspend fun setSpeechRate(rate: Float) {
        context.settingsDataStore.edit {
            it[Keys.SPEECH_RATE] = rate.coerceIn(0.5f, 1.5f)
        }
    }

    val autoPlayAudio: Flow<Boolean> = context.settingsDataStore.data.map {
        it[Keys.AUTO_PLAY_AUDIO] ?: false
    }

    suspend fun setAutoPlayAudio(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_PLAY_AUDIO] = enabled }
    }

    // ============================================================
    // 学习行为
    // ============================================================

    /** 答错的词是否在本轮末尾重现 */
    val repeatWrongAtEnd: Flow<Boolean> = context.settingsDataStore.data.map {
        it[Keys.REPEAT_WRONG] ?: true
    }

    suspend fun setRepeatWrongAtEnd(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.REPEAT_WRONG] = enabled }
    }

    /** 每次学习引入的新词上限 */
    val dailyNewLimit: Flow<Int> = context.settingsDataStore.data.map {
        (it[Keys.DAILY_NEW_LIMIT] ?: 10).coerceIn(5, 100)
    }

    suspend fun setDailyNewLimit(limit: Int) {
        context.settingsDataStore.edit {
            it[Keys.DAILY_NEW_LIMIT] = limit.coerceIn(5, 100)
        }
    }

    /** 每次学习的复习上限 */
    val dailyReviewLimit: Flow<Int> = context.settingsDataStore.data.map {
        (it[Keys.DAILY_REVIEW_LIMIT] ?: 20).coerceIn(5, 200)
    }

    suspend fun setDailyReviewLimit(limit: Int) {
        context.settingsDataStore.edit {
            it[Keys.DAILY_REVIEW_LIMIT] = limit.coerceIn(5, 200)
        }
    }

    // ============================================================
    // 缓存
    // ============================================================

    val lastCacheClear: Flow<String> = context.settingsDataStore.data.map {
        it[Keys.LAST_CACHE_CLEAR] ?: "从未清理"
    }

    suspend fun markCacheCleared(label: String) {
        context.settingsDataStore.edit { it[Keys.LAST_CACHE_CLEAR] = label }
    }

    /**
     * 清除应用缓存（TTS 音频缓存、图片缓存等）。
     *
     * 注意：**不清除数据库和 DataStore** —— 那会抹掉用户的学习记录。
     * 这是一个常见的误解，所以在这里写清楚。
     */
    suspend fun clearCache(): Long {
        val cacheDir = context.cacheDir
        var freed = 0L
        runCatching {
            cacheDir.listFiles()?.forEach { file ->
                freed += file.sizeRecursively()
                file.deleteRecursively()
            }
            // 外部缓存目录（如果存在）
            context.externalCacheDir?.let { dir ->
                dir.listFiles()?.forEach { file ->
                    freed += file.sizeRecursively()
                    file.deleteRecursively()
                }
            }
        }
        return freed
    }

    private fun java.io.File.sizeRecursively(): Long =
        if (isFile) length() else listFiles()?.sumOf { it.sizeRecursively() } ?: 0L
}
