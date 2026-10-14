package com.wordflash.app.ui.screen.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflash.app.data.local.entity.WordBook
import com.wordflash.app.data.repository.StudyRepository
import com.wordflash.app.data.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 词库列表 / 模式选择页的 ViewModel。
 */
@HiltViewModel
class ModeSelectViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val studyRepository: StudyRepository
) : ViewModel() {

    val books: StateFlow<List<WordBook>> = wordRepository.observeAllBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** 每个词库的待复习数量，key = bookId */
    private val _dueCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val dueCounts: StateFlow<Map<Long, Int>> = _dueCounts.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshDueCounts()
    }

    /**
     * 刷新各词库的待复习数量。
     *
     * 逐词库查询是 N 次查询，但词库通常只有个位数，
     * 比写一个带 GROUP BY 的聚合 SQL 更易读也更好维护。
     */
    fun refreshDueCounts(now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val current = books.value
            val counts = current.associate { book ->
                book.id to runCatching {
                    studyRepository.countDueNow(book.id, now)
                }.getOrDefault(0)
            }
            _dueCounts.value = counts
        }
    }

    /** 删除自定义词库（内置词库受保护） */
    fun deleteBook(book: WordBook) {
        if (book.isBuiltIn) {
            _message.value = "内置词库不可删除"
            return
        }
        viewModelScope.launch {
            val ok = runCatching { wordRepository.deleteCustomBook(book.id) }
                .getOrDefault(false)
            _message.value = if (ok) "已删除「${book.name}」" else "删除失败"
            refreshDueCounts()
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
