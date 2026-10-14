package com.wordflash.app.ui.screen.wordbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflash.app.data.local.entity.Word
import com.wordflash.app.data.repository.StudyRepository
import com.wordflash.app.data.repository.WordRepository
import com.wordflash.app.tts.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「我的单词」页 ViewModel：错题本 / 生词本 / 收藏 三个分页。
 *
 * 三者数据来源不同：
 * - **错题本**：`word_progress.isInWrongBook`，答错自动收录、答对自动移出
 * - **生词本**：`word_mark.isInWordbook`，仅用户手动添加
 * - **收藏**：`word_mark.isFavorite`，仅用户手动添加
 */
@HiltViewModel
class WordbookViewModel @Inject constructor(
    private val studyRepository: StudyRepository,
    private val wordRepository: WordRepository,
    private val ttsManager: TtsManager
) : ViewModel() {

    /** 当前查看的分页。错题本放第一页 —— 它是最需要复习的内容。 */
    enum class Tab(val label: String) {
        WRONG("错题本"),
        WORDBOOK("生词本"),
        FAVORITE("收藏")
    }

    data class UiState(
        val tab: Tab = Tab.WRONG,
        /** 当前分页的单词列表（已按搜索词过滤） */
        val words: List<Word> = emptyList(),
        /** 错题本每词的错误次数，key = wordId */
        val wrongCounts: Map<Long, Int> = emptyMap(),
        val wrongCount: Int = 0,
        val wordbookCount: Int = 0,
        val favoriteCount: Int = 0,
        val query: String = "",
        val isLoading: Boolean = true
    )

    private val tab = MutableStateFlow(Tab.WRONG)
    private val query = MutableStateFlow("")

    private val wrongBook = studyRepository.observeWrongBook()
    private val wordbook = studyRepository.observeWordbook()
    private val favorites = studyRepository.observeFavorites()

    val uiState: StateFlow<UiState> = combine(
        tab,
        query,
        wrongBook,
        wordbook,
        favorites
    ) { currentTab, q, wrong, wb, fav ->
        val source: List<Word> = when (currentTab) {
            Tab.WRONG -> wrong.map { it.word }
            Tab.WORDBOOK -> wb
            Tab.FAVORITE -> fav
        }
        UiState(
            tab = currentTab,
            words = filter(source, q),
            wrongCounts = wrong.associate { it.word.id to (it.progress?.wrongCount ?: 0) },
            wrongCount = wrong.size,
            wordbookCount = wb.size,
            favoriteCount = fav.size,
            query = q,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState()
    )

    private fun filter(words: List<Word>, q: String): List<Word> {
        val keyword = q.trim()
        if (keyword.isEmpty()) return words
        return words.filter {
            it.spelling.contains(keyword, ignoreCase = true) || it.meaning.contains(keyword)
        }
    }

    fun setTab(value: Tab) {
        tab.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun speak(text: String) = ttsManager.speak(text)

    // ---------- 错题本操作 ----------

    /** 手动把单词移出错题本 */
    fun removeFromWrongBook(wordId: Long) {
        viewModelScope.launch {
            runCatching { studyRepository.removeFromWrongBook(wordId) }
        }
    }

    /** 清空错题本 */
    fun clearWrongBook() {
        viewModelScope.launch {
            runCatching { studyRepository.clearWrongBook() }
        }
    }

    // ---------- 收藏 / 生词本操作 ----------

    fun toggleFavorite(wordId: Long) {
        viewModelScope.launch { runCatching { studyRepository.toggleFavorite(wordId) } }
    }

    fun toggleWordbook(wordId: Long) {
        viewModelScope.launch { runCatching { studyRepository.toggleWordbook(wordId) } }
    }
}
