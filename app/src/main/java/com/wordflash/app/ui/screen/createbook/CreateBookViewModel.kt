package com.wordflash.app.ui.screen.createbook

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflash.app.data.local.entity.BookCategory
import com.wordflash.app.data.local.entity.Word
import com.wordflash.app.data.repository.WordRepository
import com.wordflash.app.data.seed.DictPackDto
import com.wordflash.app.data.seed.DictWordDto
import com.wordflash.app.util.WordImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 新建自定义词库。
 *
 * 支持三种录入方式，共用同一个预览与保存流程：
 * 1. **逐条添加** —— 填拼写 + 释义，点「添加」
 * 2. **粘贴批量** —— 一段文本，自动识别分隔符
 * 3. **选择文件** —— CSV / TXT / JSON（原「导入词库」功能合并到这里）
 *
 * 三种方式的结果都汇入 [uiState].draftWords，最后统一保存。
 */
@HiltViewModel
class CreateBookViewModel @Inject constructor(
    private val wordRepository: WordRepository
) : ViewModel() {

    private companion object {
        const val MAX_FILE_BYTES = 5 * 1024 * 1024L   // 5 MB
        const val MAX_PASTE_LINES = 5000
    }

    data class DraftWord(
        val spelling: String,
        val meaning: String,
        val phonetic: String = "",
        val example: String = ""
    )

    data class UiState(
        val bookName: String = "",
        val nameError: String? = null,
        // 逐条录入的输入框
        val inputSpelling: String = "",
        val inputMeaning: String = "",
        val inputPhonetic: String = "",
        // 粘贴批量
        val pasteText: String = "",
        // 待保存的词
        val draftWords: List<DraftWord> = emptyList(),
        // 提示信息
        val message: String? = null,
        val isSaving: Boolean = false,
        val saved: Boolean = false
    ) {
        /** 有内容的输入才算「可以添加」 */
        val canAddSingle: Boolean
            get() = inputSpelling.isNotBlank() && inputMeaning.isNotBlank()

        val canSave: Boolean
            get() = bookName.isNotBlank() && draftWords.isNotEmpty()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ---------- 词库名 ----------

    fun setBookName(name: String) {
        _uiState.update { it.copy(bookName = name, nameError = null) }
    }

    // ---------- 逐条录入 ----------

    fun setInputSpelling(v: String) = _uiState.update { it.copy(inputSpelling = v) }
    fun setInputMeaning(v: String) = _uiState.update { it.copy(inputMeaning = v) }
    fun setInputPhonetic(v: String) = _uiState.update { it.copy(inputPhonetic = v) }

    /** 添加当前输入框里的单词 */
    fun addSingleWord() {
        val s = _uiState.value
        val spelling = s.inputSpelling.trim().lowercase()
        val meaning = s.inputMeaning.trim()
        if (spelling.isEmpty() || meaning.isEmpty()) return

        if (s.draftWords.any { it.spelling == spelling }) {
            _uiState.update { it.copy(message = "「$spelling」已在列表中") }
            return
        }

        _uiState.update {
            it.copy(
                draftWords = it.draftWords + DraftWord(
                    spelling = spelling,
                    meaning = meaning,
                    phonetic = it.inputPhonetic.trim()
                ),
                // 只清空拼写和释义，音标保留 —— 连续录入同一本书的词时
                // 通常不需要重复填，且清空更符合"下一条"的预期
                inputSpelling = "",
                inputMeaning = "",
                inputPhonetic = "",
                message = null
            )
        }
    }

    // ---------- 粘贴批量 ----------

    fun setPasteText(v: String) = _uiState.update { it.copy(pasteText = v) }

    /**
     * 解析粘贴的文本并合并进草稿列表。
     *
     * 每行一个词，支持以下分隔方式（自动识别）：
     * ```
     * abandon 放弃
     * abandon,放弃
     * abandon\t放弃
     * abandon - 放弃
     * abandon /əˈbændən/ 放弃        ← 中间带音标
     * ```
     * 只提取「第一段」作为拼写、「最后一段」作为释义，
     * 中间部分若以斜杠包裹则视为音标。
     */
    fun parsePasted() {
        val raw = _uiState.value.pasteText
        if (raw.isBlank()) {
            _uiState.update { it.copy(message = "请先粘贴内容") }
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
            if (parsed == null) {
                skipped++
            } else if (existing.contains(parsed.spelling)) {
                skipped++
            } else {
                existing += parsed.spelling
                newWords += parsed
                added++
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

    /**
     * 解析单行。返回 null 表示这行不可用。
     *
     * 优先按制表符/逗号切分；若都没有，则尝试用「第一个空格」切，
     * 这样 `abandon 放弃` 这种最常见的写法也能识别。
     */
    private fun parseLine(line: String): DraftWord? {
        // 1) 制表符或逗号：明确的分隔符
        val byTab = line.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
        if (byTab.size >= 2) {
            return DraftWord(
                spelling = byTab[0].lowercase(),
                meaning = byTab.drop(1).joinToString(" "),
                phonetic = extractPhonetic(byTab.drop(1).joinToString(" "))
            )
        }
        val byComma = line.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (byComma.size >= 2) {
            return DraftWord(
                spelling = byComma[0].lowercase(),
                meaning = byComma.drop(1).joinToString("，"),
                phonetic = extractPhonetic(byComma.drop(1).joinToString(" "))
            )
        }

        // 2) 空格：取第一个空白前的英文部分当拼写
        val m = Regex("""^([A-Za-z][A-Za-z\-'.]*)\s+(.+)$""").find(line)
        if (m != null) {
            val spelling = m.groupValues[1].lowercase()
            val rest = m.groupValues[2].trim()
            return DraftWord(
                spelling = spelling,
                meaning = rest,
                phonetic = extractPhonetic(rest)
            )
        }

        // 3) 只有英文单词、没有释义 —— 不收录（释义是必填）
        return null
    }

    /** 从文本里抠出 /.../ 形式的音标，若没有则返回空 */
    private fun extractPhonetic(text: String): String =
        Regex("""/[^/\s][^/]*/""").find(text)?.value.orEmpty()

    // ---------- 文件导入 ----------

    fun importFromFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = context.contentResolver
                    val fileName = queryFileName(context, uri) ?: "未命名文件"
                    val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                    if (size > MAX_FILE_BYTES) {
                        return@runCatching Triple(fileName, null, "文件过大（超过 5 MB）")
                    }
                    val content = resolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    } ?: return@runCatching Triple(fileName, null, "无法读取文件")

                    val suggested = fileName.substringBeforeLast('.')
                    Triple(fileName, WordImporter.parse(content, suggested), null)
                }.getOrElse { e ->
                    Triple("", null, "读取失败：${e.message}")
                }
            }

            val (fileName, parsed, readError) = result
            if (readError != null) {
                _uiState.update { it.copy(message = readError) }
                return@launch
            }
            if (parsed == null || !parsed.isSuccess) {
                _uiState.update { it.copy(message = parsed?.error ?: "解析失败") }
                return@launch
            }

            val pack = parsed.pack!!
            val existing = _uiState.value.draftWords.map { it.spelling }.toMutableSet()
            val incoming = pack.words
                .map {
                    DraftWord(
                        spelling = it.spelling.trim().lowercase(),
                        meaning = it.meaning.trim(),
                        phonetic = it.phonetic,
                        example = it.example
                    )
                }
                .filter { it.spelling.isNotEmpty() && it.meaning.isNotEmpty() }
                .filter { existing.add(it.spelling) }

            _uiState.update {
                it.copy(
                    // 文件名作为词库名的建议值，用户没填过才采用
                    bookName = it.bookName.ifBlank { pack.name.ifBlank { fileName.substringBeforeLast('.') } },
                    draftWords = it.draftWords + incoming,
                    message = "从 $fileName 导入 ${incoming.size} 个单词"
                )
            }
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()

    // ---------- 草稿管理 ----------

    fun removeDraft(index: Int) {
        _uiState.update { s ->
            if (index !in s.draftWords.indices) s
            else s.copy(draftWords = s.draftWords.filterIndexed { i, _ -> i != index })
        }
    }

    fun clearDrafts() {
        _uiState.update { it.copy(draftWords = emptyList(), message = "已清空列表") }
    }

    /** 从内置词库复制一份作为起点（让用户能"改着用"） */
    fun loadBuiltInSample(source: List<Word>) {
        val existing = _uiState.value.draftWords.map { it.spelling }.toMutableSet()
        val incoming = source
            .map {
                DraftWord(
                    spelling = it.spelling,
                    meaning = it.meaning,
                    phonetic = it.phonetic,
                    example = it.example
                )
            }
            .filter { existing.add(it.spelling) }
        _uiState.update {
            it.copy(
                draftWords = it.draftWords + incoming,
                message = "已载入 ${incoming.size} 个单词作为草稿"
            )
        }
    }

    // ---------- 保存 ----------

    fun save() {
        val s = _uiState.value
        if (s.bookName.isBlank()) {
            _uiState.update { it.copy(nameError = "请填写词库名称") }
            return
        }
        if (s.draftWords.isEmpty()) {
            _uiState.update { it.copy(message = "还没有添加任何单词") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val pack = DictPackDto(
                name = s.bookName.trim(),
                description = "用户创建",
                category = BookCategory.CUSTOM,
                words = s.draftWords.map {
                    DictWordDto(
                        spelling = it.spelling,
                        phonetic = it.phonetic,
                        meaning = it.meaning,
                        example = it.example
                    )
                }
            )

            runCatching { wordRepository.importPack(pack) }
                .onSuccess { outcome ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            saved = true,
                            message = when {
                                outcome.isEmpty -> "这些单词已存在"
                                outcome.duplicated > 0 ->
                                    "已保存 ${outcome.inserted} 词，跳过重复 ${outcome.duplicated} 词"
                                else -> "已保存 ${outcome.inserted} 个单词"
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

    /** 供调用方判断是否需要刷新词库列表 */
    fun consumedSavedFlag() {
        _uiState.update { it.copy(saved = false) }
    }
}
