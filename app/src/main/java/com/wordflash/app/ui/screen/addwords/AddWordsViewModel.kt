package com.wordflash.app.ui.screen.addwords

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflash.app.data.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 向已有词库追加单词。
 *
 * 与 [com.wordflash.app.ui.screen.createbook.CreateBookViewModel] 的区别：
 * - 词库已经存在，用户**不能改名**（只读显示，避免误改内置词库）
 * - 保存走 [WordRepository.appendWords]，直接写进该词库
 *
 * 内置词库同样允许追加 —— 「四级核心词汇」缺了 apple，
 * 用户本来就该能补进去，这是这个页面存在的理由。
 */
@HiltViewModel
class AddWordsViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private companion object {
        const val MAX_PASTE_LINES = 5000
    }

    /** 词库 id 由路由参数注入，见 [com.wordflash.app.ui.navigation.Routes.ADD_WORDS] */
    private val bookId: Long = requireNotNull(savedStateHandle.get<String>("bookId")) {
        "add_words 路由缺少 bookId 参数"
    }.toLong()

    data class UiState(
        val bookName: String = "",
        /** 词库当前已有多少词，显示在标题下方让用户心里有数 */
        val existingCount: Int = 0,
        val inputSpelling: String = "",
        val inputMeaning: String = "",
        val inputPhonetic: String = "",
        val pasteText: String = "",
        val draftWords: List<DraftWord> = emptyList(),
        val message: String? = null,
        val isSaving: Boolean = false,
        val saved: Boolean = false,
        /** 词库在别处被删掉了，界面要提示并阻止保存 */
        val bookMissing: Boolean = false
    ) {
        val canAddSingle: Boolean
            get() = inputSpelling.isNotBlank() && inputMeaning.isNotBlank()

        val canSave: Boolean
            get() = draftWords.isNotEmpty() && !bookMissing
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            val book = wordRepository.getBook(bookId)
            if (book == null) {
                _uiState.update {
                    it.copy(bookMissing = true, message = "这个词库已经不存在了")
                }
                return@launch
            }
            _uiState.update {
                it.copy(bookName = book.name, existingCount = book.wordCount)
            }
        }
    }

    // ---------- 逐个添加 ----------

    fun setInputSpelling(v: String) = _uiState.update { it.copy(inputSpelling = v) }
    fun setInputMeaning(v: String) = _uiState.update { it.copy(inputMeaning = v) }
    fun setInputPhonetic(v: String) = _uiState.update { it.copy(inputPhonetic = v) }

    fun addSingleWord() {
        val s = _uiState.value
        val spelling = s.inputSpelling.trim().lowercase()
        val meaning = s.inputMeaning.trim()
        if (spelling.isEmpty() || meaning.isEmpty()) return

        if (s.draftWords.any { it.spelling == spelling }) {
            _uiState.update { it.copy(message = "「$spelling」已经在下面的列表里了") }
            return
        }

        _uiState.update {
            it.copy(
                draftWords = it.draftWords + DraftWord(
                    spelling = spelling,
                    meaning = meaning,
                    phonetic = it.inputPhonetic.trim()
                ),
                inputSpelling = "",
                inputMeaning = "",
                inputPhonetic = "",
                message = null
            )
        }
    }

    // ---------- 批量粘贴 ----------

    fun setPasteText(v: String) = _uiState.update { it.copy(pasteText = v) }

    /**
     * 解析粘贴的文本并合并进草稿列表。
     *
     * 每行一个词，支持空格 / 逗号 / 制表符分隔，中间可带 /.../ 音标：
     * ```
     * apple 苹果
     * apple,苹果
     * apple\t苹果
     * apple /ˈæpl/ 苹果
     * ```
     * 只取第一段当拼写、最后一段当释义，中间若被斜杠包裹则视为音标。
     */
    fun parsePasted() {
        val raw = _uiState.value.pasteText
        if (raw.isBlank()) {
            _uiState.update { it.copy(message = "请先粘贴要添加的单词") }
            return
        }

        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_PASTE_LINES)

        var added = 0
        var skipped = 0
        val newWords = ArrayList<DraftWord>(lines.size)
        val existing = _uiState.value.draftWords.map { it.spelling }.toMutableSet()

        lines.forEach { line ->
            val parsed = parseLine(line)
            when {
                parsed == null -> skipped++
                !existing.add(parsed.spelling) -> skipped++
                else -> {
                    newWords += parsed
                    added++
                }
            }
        }

        _uiState.update {
            it.copy(
                draftWords = it.draftWords + newWords,
                pasteText = "",
                message = buildString {
                    append("解析出 $added 个单词")
                    if (skipped > 0) append("，跳过 $skipped 行（重复或格式不符）")
                }
            )
        }
    }

    private fun parseLine(line: String): DraftWord? {
        val byTab = line.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
        if (byTab.size >= 2) {
            val rest = byTab.drop(1).joinToString(" ")
            return DraftWord(byTab[0].lowercase(), rest, extractPhonetic(rest))
        }
        val byComma = line.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (byComma.size >= 2) {
            val rest = byComma.drop(1).joinToString("，")
            return DraftWord(byComma[0].lowercase(), rest, extractPhonetic(rest))
        }

        // 空格分隔：开头必须是英文单词，后面跟释义
        val m = Regex("""^([A-Za-z][A-Za-z\-'.]*)\s+(.+)$""").find(line)
        if (m != null) {
            val rest = m.groupValues[2].trim()
            return DraftWord(m.groupValues[1].lowercase(), rest, extractPhonetic(rest))
        }

        // 只有英文单词、没有释义 —— 不收（释义必填）
        return null
    }

    private fun extractPhonetic(text: String): String =
        Regex("""/[^/\s][^/]*/""").find(text)?.value.orEmpty()

    // ---------- 草稿管理 ----------

    fun removeDraft(index: Int) {
        _uiState.update { s ->
            if (index !in s.draftWords.indices) s
            else s.copy(draftWords = s.draftWords.filterIndexed { i, _ -> i != index })
        }
    }

    fun clearDrafts() {
        _uiState.update { it.copy(draftWords = emptyList(), message = "列表已清空") }
    }

    // ---------- 保存 ----------

    fun save() {
        val s = _uiState.value
        if (s.bookMissing) return
        if (s.draftWords.isEmpty()) {
            _uiState.update { it.copy(message = "还没有添加任何单词") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            runCatching {
                wordRepository.appendWords(bookId, s.draftWords.map { it.toDto() })
            }
                .onSuccess { outcome ->
                    if (outcome == null) {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                bookMissing = true,
                                message = "这个词库已经不存在了"
                            )
                        }
                        return@onSuccess
                    }
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saved = true,
                            message = when {
                                outcome.isEmpty -> "这些单词都已经在词库里了"
                                outcome.duplicated > 0 ->
                                    "已添加 ${outcome.inserted} 个，跳过重复 ${outcome.duplicated} 个"
                                else -> "已添加 ${outcome.inserted} 个单词"
                            }
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isSaving = false, message = "保存失败：${e.message}")
                    }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumedSavedFlag() {
        _uiState.update { it.copy(saved = false) }
    }
}
