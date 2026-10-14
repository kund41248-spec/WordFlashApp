package com.wordflash.app.ui.screen.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflash.app.data.local.entity.DailyStats
import com.wordflash.app.data.repository.StatsRepository
import com.wordflash.app.data.repository.StudyRepository
import com.wordflash.app.util.DateUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 统计页 ViewModel。
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val studyRepository: StudyRepository
) : ViewModel() {

    /** 图表时间范围 */
    enum class Range(val days: Int, val label: String) {
        WEEK(7, "近 7 天"),
        MONTH(30, "近 30 天"),
        QUARTER(90, "近 90 天")
    }

    data class UiState(
        val range: Range = Range.WEEK,
        val totalReviews: Int = 0,
        val totalDurationMs: Long = 0L,
        val accuracy: Float = 0f,
        val streak: Int = 0,
        val studyDays: Int = 0,
        val masteredCount: Int = 0,
        val dailyList: List<DailyStats> = emptyList(),
        val upcomingLoad: List<Pair<Long, Int>> = emptyList(),
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun setRange(range: Range) {
        _uiState.value = _uiState.value.copy(range = range)
        load()
    }

    fun load(now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val range = _uiState.value.range

            // 累计数据
            val totalReviews = runCatching { statsRepository.getTotalReviewsOnce() }
                .getOrDefault(0)
            val totalDuration = runCatching { statsRepository.getTotalDurationOnce() }
                .getOrDefault(0L)
            val totalCorrect = runCatching { statsRepository.getTotalCorrectOnce() }
                .getOrDefault(0)
            val streak = runCatching { statsRepository.calculateStreak() }.getOrDefault(0)

            // 区间内的每日数据，用于图表
            val from = DateUtils.formatDate(now - range.days * 24L * 60 * 60 * 1000)
            val to = DateUtils.formatDate(now)
            val daily = runCatching { statsRepository.getRange(from, to) }
                .getOrDefault(emptyList())

            // 未来 7 天的复习量预测
            val upcoming = runCatching {
                studyRepository.getUpcomingLoad(now, now + 7 * 24L * 60 * 60 * 1000)
            }.getOrDefault(emptyList())

            _uiState.value = _uiState.value.copy(
                totalReviews = totalReviews,
                totalDurationMs = totalDuration,
                accuracy = if (totalReviews == 0) 0f else totalCorrect.toFloat() / totalReviews,
                streak = streak,
                studyDays = statsRepository.getStudyDaysOnce(),
                dailyList = daily,
                upcomingLoad = upcoming.map { it.dayStart to it.count },
                isLoading = false
            )
        }
    }
}
