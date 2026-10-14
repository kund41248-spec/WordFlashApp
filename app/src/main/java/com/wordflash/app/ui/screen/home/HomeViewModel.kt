package com.wordflash.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflash.app.data.repository.StatsRepository
import com.wordflash.app.data.repository.StudyRepository
import com.wordflash.app.data.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页 ViewModel。
 *
 * 聚合「待复习数 / 今日新学 / 连续打卡 / 今日时长 / 默认词库」。
 * 这些数据来自三个仓库，用一个统一 UiState 承载，
 * 避免 UI 层订阅多个 Flow 再自行拼装。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val studyRepository: StudyRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    data class UiState(
        val dueCount: Int = 0,
        val todayLearned: Int = 0,
        val streak: Int = 0,
        val todayDurationMs: Long = 0L,
        /** 默认进入的词库：优先第一个内置词库 */
        val defaultBookId: Long? = null,
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /**
     * 加载首页数据。
     *
     * 用一次性查询而不是长期订阅：首页只是概览，
     * 用户从学习页返回时会重新触发刷新。
     */
    fun load(now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            // 今日汇总
            val today = runCatching { statsRepository.getLatest() }.getOrNull()

            // 词库列表
            val books = runCatching { wordRepository.getAllBooksOnce() }
                .getOrDefault(emptyList())

            // 各词库待复习数量求和
            val totalDue = books.sumOf { book ->
                runCatching { studyRepository.countDueNow(book.id, now) }.getOrDefault(0)
            }

            val streak = runCatching { statsRepository.calculateStreak() }.getOrDefault(0)

            _uiState.update {
                it.copy(
                    dueCount = totalDue,
                    todayLearned = today?.learnedCount ?: 0,
                    streak = streak,
                    todayDurationMs = today?.durationMs ?: 0L,
                    defaultBookId = books.firstOrNull { b -> b.isBuiltIn }?.id
                        ?: books.firstOrNull()?.id,
                    isLoading = false
                )
            }
        }
    }
}
