package com.wordflash.app.ui.screen.wordbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflash.app.data.local.entity.Word
import com.wordflash.app.data.local.entity.WordMark
import com.wordflash.app.data.repository.StudyRepository
import com.wordflash.app.data.repository.WordRepository
import com.wordflash.app.domain.model.WordWithProgress
import com.wordflash.app.tts.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
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
 * - **错题本**：`word_progress.isInWrongBook`，答错自动收录、也可手动增删
 * - **生词本**：`word_mark.isInWordbook`，仅用户手动添加
 * - **收藏**：`word_mark.isFavorite`，仅用户手动添加
 *
 * 错因与标签存在 `word_mark`，与错题本的成员资格（在 `word_progress`）
 * 分属两张表，所以这里要合并两个数据源。
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

    /** 错题本的快捷筛选 */
    enum class WrongFilter(val label: String) {
        ALL("全部"),
        WEAK("薄弱"),
        HAS_REASON("有错因")
    }

    data class UiState(
        val tab: Tab = Tab.WRONG,
        /** 当前分页的单词列表（已按搜索词与筛选条件过滤） */
        val words: List<Word> = emptyList(),
        /** 错题本每词的错误次数，key = wordId */
        val wrongCounts: Map<Long, Int> = emptyMap(),
        /** 错题本每词的错因，key = wordId；空串表示没写 */
        val wrongReasons: Map<Long, String> = emptyMap(),
        /** 错题本每词的标签，key = wordId */
        val wrongTags: Map<Long, List<String>> = emptyMap(),
        /** 所有出现过的标签，供筛选面板列出 */
        val allTags: List<String> = emptyList(),
        val wrongCount: Int = 0,
        val wordbookCount: Int = 0,
        val favoriteCount: Int = 0,
        val query: String = "",
        val filter: WrongFilter = WrongFilter.ALL,
        /** 正在编辑错因的词 id，null 表示没有在编辑 */
        val editingReasonWordId: Long? = null,
        val isLoading: Boolean = true
    )

    private val tab = MutableStateFlow(Tab.WRONG)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(WrongFilter.ALL)
    private val editingReasonWordId = MutableStateFlow<Long?>(null)

    private val wrongBook = studyRepository.observeWrongBook()
    private val wordbook = studyRepository.observeWordbook()
    private val favorites = studyRepository.observeFavorites()
    private val marks = studyRepository.observeMarksWithWrongMeta()
    private val allTags = studyRepository.observeAllWrongTags()

    /**
     * 筛选条件打包成一个流。
     *
     * 不用 combine 的多流重载（最多支持 5 个），是因为需要合并的流有 8 个，
     * 而嵌套 combine 再解构三元组会让类型推断变得难以阅读。
     * 统一先归并成 3 个中间流，语义反而更清楚。
     */
    private data class Filters(val tab: Tab, val query: String, val filter: WrongFilter)
    private data class Lists(
        val wrong: List<WordWithProgress>,
        val wordbook: List<Word>,
        val favorites: List<Word>
    )
    private data class Meta(
        val marks: List<WordMark>,
        val tags: List<String>,
        val editingReason: Long?
    )

    private val filtersFlow: Flow<Filters> =
        combine(tab, query, filter) { t, q, f -> Filters(t, q, f) }

    private val listsFlow: Flow<Lists> =
        combine(wrongBook, wordbook, favorites) { w, wb, fav -> Lists(w, wb, fav) }

    private val metaFlow: Flow<Meta> =
        combine(marks, allTags, editingReasonWordId) { m, t, e -> Meta(m, t, e) }

    val uiState: StateFlow<UiState> = combine(
        filtersFlow,
        listsFlow,
        metaFlow
    ) { f, l, m ->
        val reasonMap = m.marks.associate { it.wordId to it.wrongReason }
        val tagMap = m.marks.associate { it.wordId to it.tagList }

        val source: List<Word> = when (f.tab) {
            Tab.WRONG -> l.wrong.map { it.word }
            Tab.WORDBOOK -> l.wordbook
            Tab.FAVORITE -> l.favorites
        }

        val filtered = filterWords(
            words = source,
            keyword = f.query,
            filter = if (f.tab == Tab.WRONG) f.filter else WrongFilter.ALL,
            reasons = reasonMap,
            tags = tagMap
        )

        UiState(
            tab = f.tab,
            words = filtered,
            wrongCounts = l.wrong.associate { it.word.id to (it.progress?.wrongCount ?: 0) },
            wrongReasons = reasonMap,
            wrongTags = tagMap,
            // 分号分隔的多标签要先摊平再取唯一值
            allTags = m.tags
                .flatMap { row -> row.split(WordMark.TAG_SEPARATOR) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted(),
            wrongCount = l.wrong.size,
            wordbookCount = l.wordbook.size,
            favoriteCount = l.favorites.size,
            query = f.query,
            filter = f.filter,
            editingReasonWordId = m.editingReason,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState()
    )

    /**
     * 过滤。
     *
     * 除拼写与释义外，错因和标签也参与匹配 —— 用户搜「形近词」
     * 时想找的是打了这个标签的词，而不是释义里含这三个字的词。
     */
    private fun filterWords(
        words: List<Word>,
        keyword: String,
        filter: WrongFilter,
        reasons: Map<Long, String>,
        tags: Map<Long, List<String>>
    ): List<Word> {
        var result = words

        // 先按快捷筛选收窄
        result = when (filter) {
            WrongFilter.ALL -> result
            WrongFilter.WEAK -> result.filter { w ->
                tags[w.id]?.any {
                    it.equals(StudyRepository.TAG_WEAK, ignoreCase = true)
                } == true
            }
            WrongFilter.HAS_REASON -> result.filter { w ->
                !reasons[w.id].isNullOrBlank()
            }
        }

        val k = keyword.trim()
        if (k.isEmpty()) return result

        return result.filter { w ->
            w.spelling.contains(k, ignoreCase = true) ||
                w.meaning.contains(k) ||
                reasons[w.id]?.contains(k, ignoreCase = true) == true ||
                tags[w.id]?.any { it.contains(k, ignoreCase = true) } == true
        }
    }

    fun setTab(value: Tab) {
        tab.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: WrongFilter) {
        filter.value = value
    }

    fun speak(text: String) = ttsManager.speak(text)

    // ---------- 错题本成员操作 ----------

    /** 手动把单词移出错题本 */
    fun removeFromWrongBook(wordId: Long) {
        viewModelScope.launch {
            runCatching { studyRepository.removeFromWrongBook(wordId) }
        }
    }

    /** 手动把单词加进错题本 */
    fun addToWrongBook(wordId: Long) {
        viewModelScope.launch {
            runCatching { studyRepository.addToWrongBook(wordId) }
        }
    }

    /** 清空错题本 */
    fun clearWrongBook() {
        viewModelScope.launch {
            runCatching { studyRepository.clearWrongBook() }
        }
    }

    // ---------- 错因与标签 ----------

    fun startEditingReason(wordId: Long) {
        editingReasonWordId.value = wordId
    }

    fun stopEditingReason() {
        editingReasonWordId.value = null
    }

    fun saveReason(wordId: Long, reason: String) {
        editingReasonWordId.value = null
        viewModelScope.launch {
            runCatching { studyRepository.saveWrongReason(wordId, reason) }
        }
    }

    /** 覆盖设置标签（用于编辑弹窗保存整份标签） */
    fun saveTags(wordId: Long, tags: List<String>) {
        viewModelScope.launch {
            runCatching { studyRepository.saveWrongTags(wordId, tags) }
        }
    }

    /** 标记为薄弱：打上「薄弱」标签 */
    fun markWeak(wordId: Long) {
        viewModelScope.launch {
            runCatching { studyRepository.addWrongTag(wordId, StudyRepository.TAG_WEAK) }
        }
    }

    /** 取消薄弱标记 */
    fun unmarkWeak(wordId: Long) {
        viewModelScope.launch {
            runCatching { studyRepository.removeWrongTag(wordId, StudyRepository.TAG_WEAK) }
        }
    }

    /**
     * 标记为已掌握：打上「已掌握」标签并移出错题本。
     *
     * 两步都做，是因为它们的含义不同：
     * 移出错题本表示「不用再练了」，标签则留下痕迹，
     * 之后在全部单词里还能看出这个词是被主动判定掌握的。
     */
    fun markMastered(wordId: Long) {
        viewModelScope.launch {
            runCatching {
                studyRepository.addWrongTag(wordId, StudyRepository.TAG_MASTERED)
                studyRepository.removeFromWrongBook(wordId)
            }
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
