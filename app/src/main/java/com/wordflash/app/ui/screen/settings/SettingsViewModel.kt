package com.wordflash.app.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflash.app.data.repository.SettingsRepository
import com.wordflash.app.data.repository.StatsRepository
import com.wordflash.app.data.repository.WordRepository
import com.wordflash.app.tts.TtsManager
import com.wordflash.app.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 设置页 ViewModel。
 *
 * ## 关于那个 combine 链
 *
 * Kotlin 标准库的 `combine` 最多接收 5 个 Flow。本页有 7 项设置，
 * 因此先用一个 5 元 combine 打底，再用 `.combine(flow) { a, b -> }`
 * 逐个追加 —— 这是官方推荐的扩展写法，比拼 List<Flow> 更类型安全。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val wordRepository: WordRepository,
    private val statsRepository: StatsRepository,
    private val ttsManager: TtsManager
) : ViewModel() {

    data class UiState(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val dynamicColor: Boolean = false,
        val speechRate: Float = 1.0f,
        val autoPlayAudio: Boolean = false,
        val repeatWrongAtEnd: Boolean = true,
        val dailyNewLimit: Int = 10,
        val dailyReviewLimit: Int = 20,
        val lastCacheClear: String = "从未清理",
        val ttsAvailable: Boolean = false,
        val bookCount: Int = 0,
        val wordCount: Int = 0
    )

    private val stats = MutableStateFlow(StatsCounts())

    private data class StatsCounts(val books: Int = 0, val words: Int = 0)

    val uiState: StateFlow<UiState> = combine(
        settingsRepository.themeMode,
        settingsRepository.dynamicColor,
        settingsRepository.speechRate,
        settingsRepository.autoPlayAudio,
        settingsRepository.repeatWrongAtEnd
    ) { theme, dynamic, rate, autoPlay, repeat ->
        UiState(
            themeMode = theme,
            dynamicColor = dynamic,
            speechRate = rate,
            autoPlayAudio = autoPlay,
            repeatWrongAtEnd = repeat
        )
    }.combine(settingsRepository.dailyNewLimit) { ui, n ->
        ui.copy(dailyNewLimit = n)
    }.combine(settingsRepository.dailyReviewLimit) { ui, n ->
        ui.copy(dailyReviewLimit = n)
    }.combine(settingsRepository.lastCacheClear) { ui, s ->
        ui.copy(lastCacheClear = s)
    }.combine(ttsManager.status) { ui, status ->
        ui.copy(ttsAvailable = status == TtsManager.Status.READY)
    }.combine(stats) { ui, s ->
        ui.copy(bookCount = s.books, wordCount = s.words)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState()
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        // 把已保存的语速推给 TTS 引擎，保证首次朗读就用对语速
        viewModelScope.launch {
            settingsRepository.speechRate.collect { ttsManager.setSpeechRate(it) }
        }
        refreshCounts()
    }

    private fun refreshCounts() {
        viewModelScope.launch {
            val books = runCatching { wordRepository.getAllBooksOnce() }.getOrDefault(emptyList())
            stats.value = StatsCounts(
                books = books.size,
                words = books.sumOf { it.wordCount }
            )
        }
    }

    // ============================================================
    // 主题
    // ============================================================

    fun setThemeMode(mode: ThemeMode) = launchSet { settingsRepository.setThemeMode(mode) }

    fun setDynamicColor(enabled: Boolean) =
        launchSet { settingsRepository.setDynamicColor(enabled) }

    // ============================================================
    // 发音
    // ============================================================

    fun setSpeechRate(rate: Float) {
        viewModelScope.launch {
            settingsRepository.setSpeechRate(rate)
            // 立刻生效，用户可边拖边试听
            ttsManager.setSpeechRate(rate)
        }
    }

    fun setAutoPlayAudio(enabled: Boolean) =
        launchSet { settingsRepository.setAutoPlayAudio(enabled) }

    /** 试听当前语速 */
    fun previewSpeech() = ttsManager.speak("vocabulary")

    // ============================================================
    // 学习行为
    // ============================================================

    fun setRepeatWrongAtEnd(enabled: Boolean) =
        launchSet { settingsRepository.setRepeatWrongAtEnd(enabled) }

    fun setDailyNewLimit(limit: Int) = launchSet { settingsRepository.setDailyNewLimit(limit) }

    fun setDailyReviewLimit(limit: Int) =
        launchSet { settingsRepository.setDailyReviewLimit(limit) }

    // ============================================================
    // 数据操作
    // ============================================================

    fun clearCache() {
        viewModelScope.launch {
            val freed = runCatching { settingsRepository.clearCache() }.getOrDefault(0L)
            val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
            val label = "${formatSize(freed)}（$time）"
            settingsRepository.markCacheCleared(label)
            _message.value = if (freed > 0) "已清理缓存 $label" else "没有可清理的缓存"
        }
    }

    /**
     * 清空学习记录。
     *
     * 只清进度与统计，**保留词库** —— 用户重装或想重头开始时用。
     */
    fun clearStudyData() {
        viewModelScope.launch {
            runCatching { statsRepository.clearAll() }
                .onSuccess { _message.value = "学习记录已清空（词库保留）" }
                .onFailure { _message.value = "清空失败：${it.message}" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun launchSet(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
