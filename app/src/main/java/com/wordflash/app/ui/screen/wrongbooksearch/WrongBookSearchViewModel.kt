package com.wordflash.app.ui.screen.wrongbooksearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflash.app.data.local.entity.Word
import com.wordflash.app.data.repository.StudyRepository
import com.wordflash.app.data.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 搜索单词并加入错题本。
 *
 * 解决一个具体的缺口：错题本原本只能靠「答题答错」自动收录，
 * 但用户想练的词未必在做错的题里出现过 —— 比如老师划的重点词、
 * 自己觉得记不牢的词。这个页面让他们能主动补进去。
 */
@HiltViewModel
class WrongBookSearchViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val studyRepository: StudyRepository
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val results: List<Word> = emptyList(),
        /** 已加入错题本的词 id */
        val added: Set<Long> = emptySet(),
        val isSearching: Boolean = false,
        val message: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        // 输入停顿 300ms 再查库，避免每敲一个字母都全表扫描
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            queryFlow.debounce(300).collect { q ->
                runSearch(q)
            }
        }
    }

    private suspend fun runSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        val found = runCatching { wordRepository.search(q) }.getOrDefault(emptyList())
        _uiState.update { it.copy(results = found, isSearching = false) }
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        queryFlow.value = value
    }

    /** 加入错题本。已加入的再点一次就取消。 */
    fun toggle(wordId: Long) {
        val already = _uiState.value.added.contains(wordId)
        viewModelScope.launch {
            runCatching {
                if (already) {
                    studyRepository.removeFromWrongBook(wordId)
                } else {
                    studyRepository.addToWrongBook(wordId)
                }
            }.onSuccess {
                _uiState.update { s ->
                    s.copy(
                        added = if (already) s.added - wordId else s.added + wordId,
                        message = if (already) "已移出错题本" else "已加入错题本"
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(message = "操作失败：${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
