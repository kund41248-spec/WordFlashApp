package com.wordflash.app.ui.screen.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflash.app.data.repository.SettingsRepository
import com.wordflash.app.data.repository.StudyRepository
import com.wordflash.app.data.repository.WordRepository
import com.wordflash.app.domain.model.SessionConfig
import com.wordflash.app.domain.model.StudyMode
import com.wordflash.app.domain.session.StudySessionManager
import com.wordflash.app.tts.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 学习页 ViewModel。
 *
 * 职责很薄：把 [StudySessionManager] 的状态转发给 UI，
 * 并处理「用户点了发音按钮」这类 UI 专属动作。
 *
 * 业务逻辑（调度、统计、队列）全在 domain / data 层，
 * ViewModel 不做决策，只做适配。
 */
@HiltViewModel
class StudyViewModel @Inject constructor(
    private val sessionManager: StudySessionManager,
    private val wordRepository: WordRepository,
    private val studyRepository: StudyRepository,
    private val settingsRepository: SettingsRepository,
    private val ttsManager: TtsManager
) : ViewModel() {

    private companion object {
        /** 错题专项练习一次最多练多少个词 */
        const val WRONG_PRACTICE_LIMIT = 100

        /** 选择题作答后停留多久再进入下一题（毫秒），让用户看到对错反馈 */
        const val ANSWER_FEEDBACK_DELAY_MS = 700L
    }

    /** 会话状态，直接来自 domain 层 */
    val sessionState: StateFlow<StudySessionManager.State> = sessionManager.state

    /** TTS 可用性，UI 据此决定是否显示发音按钮 */
    val ttsStatus: StateFlow<TtsManager.Status> = ttsManager.status

    private val _bookName = MutableStateFlow("")
    val bookName: StateFlow<String> = _bookName.asStateFlow()

    private val _autoPlay = MutableStateFlow(false)
    val autoPlay: StateFlow<Boolean> = _autoPlay.asStateFlow()

    /** 当前已选择的选项（选择题模式），用于高亮对错 */
    private val _selectedOption = MutableStateFlow<String?>(null)
    val selectedOption: StateFlow<String?> = _selectedOption.asStateFlow()

    /**
     * 开始学习。
     *
     * 学习配额与「答错重现」等行为从设置读取，
     * 保证用户在设置页的改动立即在本轮生效。
     *
     * @param modeKey StudyMode 的 key，来自导航参数
     */
    fun start(bookId: Long, modeKey: String, autoPlayAudio: Boolean? = null) {
        val mode = StudyMode.fromKey(modeKey)

        viewModelScope.launch {
            _bookName.value = wordRepository.getBook(bookId)?.name ?: ""

            // 从设置读取本轮的配额与行为开关
            val reviewLimit = settingsRepository.dailyReviewLimit.first()
            val newLimit = settingsRepository.dailyNewLimit.first()
            val repeatWrong = settingsRepository.repeatWrongAtEnd.first()
            val autoPlay = autoPlayAudio ?: settingsRepository.autoPlayAudio.first()
            _autoPlay.value = autoPlay

            sessionManager.start(
                SessionConfig(
                    bookId = bookId,
                    mode = mode,
                    reviewLimit = reviewLimit,
                    newWordLimit = newLimit,
                    repeatWrongAtEnd = repeatWrong,
                    autoPlayAudio = autoPlay
                )
            )

            if (autoPlay) {
                sessionState.value.currentCard?.let { speak(it.spelling) }
            }
        }
    }

    /**
     * 开始「错题专项练习」。
     *
     * 与普通学习的区别：不按词库和到期时间取词，
     * 而是把错题本里的词整批拿来练（最多 [WRONG_PRACTICE_LIMIT] 个）。
     *
     * 新词配额设为 0 —— 专项练习就只练错题，不掺新词。
     */
    fun startWrongPractice(modeKey: String, autoPlayAudio: Boolean? = null) {
        val mode = StudyMode.fromKey(modeKey)

        viewModelScope.launch {
            _bookName.value = "错题本"

            val wordIds = runCatching {
                studyRepository.getWrongBookWordIds(WRONG_PRACTICE_LIMIT)
            }.getOrDefault(emptyList())

            if (wordIds.isEmpty()) {
                // 错题本空了（可能刚被清空），直接结束
                sessionManager.clear()
                return@launch
            }

            val repeatWrong = settingsRepository.repeatWrongAtEnd.first()
            val autoPlay = autoPlayAudio ?: settingsRepository.autoPlayAudio.first()
            _autoPlay.value = autoPlay

            sessionManager.start(
                SessionConfig(
                    bookId = 0L,
                    mode = mode,
                    reviewLimit = wordIds.size,
                    newWordLimit = 0,
                    wordIds = wordIds,
                    repeatWrongAtEnd = repeatWrong,
                    autoPlayAudio = autoPlay
                )
            )

            if (autoPlay) {
                sessionState.value.currentCard?.let { speak(it.spelling) }
            }
        }
    }

    // ============================================================
    // 卡片模式
    // ============================================================

    /** 翻面 */
    fun flip() = sessionManager.flip()

    /** 认识 */
    fun markKnown() = submit(true)

    /** 不认识 */
    fun markUnknown() = submit(false)

    /**
     * 提交卡片模式的作答。
     *
     * 「对错判断」不在这里 —— 卡片模式是用户自评的，
     * 点「认识」就是答对，逻辑很直接，因此直接透传给会话管理器。
     */
    private fun submit(isCorrect: Boolean) {
        viewModelScope.launch {
            sessionManager.answer(isCorrect)
            maybeAutoPlayNext()
        }
    }

    // ============================================================
    // 选择题模式
    // ============================================================

    /**
     * 选择了某个选项。
     *
     * 选择题的判断放在 ViewModel 而不是 UI：UI 只上报「用户点了什么」，
     * 「这个选项对不对」是业务判断。
     *
     * @param option 选项文本
     * @param correctAnswer 正确答案
     * @param speakOnCorrect 答对后是否朗读（听音辨词模式通常不需要，
     *        因为题目本身就是听发音）
     */
    fun selectOption(option: String, correctAnswer: String, speakOnCorrect: Boolean = false) {
        if (_selectedOption.value != null) return    // 已作答，忽略重复点击
        _selectedOption.value = option

        val isCorrect = option == correctAnswer
        if (isCorrect && speakOnCorrect) {
            sessionState.value.currentCard?.let { speak(it.spelling) }
        }

        viewModelScope.launch {
            sessionManager.answer(isCorrect)
            // 延迟后清空选择并自动播放下一题
            kotlinx.coroutines.delay(ANSWER_FEEDBACK_DELAY_MS)
            _selectedOption.value = null
            maybeAutoPlayNext()
        }
    }

    // ============================================================
    // 拼写模式
    // ============================================================

    /**
     * 提交拼写答案。
     *
     * 比较时**忽略大小写与首尾空格** —— 用户手打英文很容易
     * 多一个空格或大小写不一致，不该判错。
     */
    fun submitSpelling(input: String, expected: String) {
        val isCorrect = input.trim().equals(expected.trim(), ignoreCase = true)
        viewModelScope.launch {
            sessionManager.answer(isCorrect)
            maybeAutoPlayNext()
        }
    }

    // ============================================================
    // 发音
    // ============================================================

    fun speak(text: String) {
        ttsManager.speak(text)
    }

    /** 朗读当前单词 */
    fun speakCurrent() {
        sessionState.value.currentCard?.let { ttsManager.speak(it.spelling) }
    }

    fun stopSpeaking() = ttsManager.stop()

    private fun maybeAutoPlayNext() {
        if (!_autoPlay.value) return
        sessionState.value.currentCard?.let { ttsManager.speak(it.spelling) }
    }

    // ============================================================
    // 生命周期
    // ============================================================

    /** 中途退出：进度已逐题落库，这里只标记结束 */
    fun abandon() {
        ttsManager.stop()
        sessionManager.abandon()
    }

    /** 离开页面时清理，避免下次进入残留上次的卡片 */
    fun clear() {
        ttsManager.stop()
        sessionManager.clear()
        _selectedOption.value = null
        _bookName.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        // 只停发音，不 shutdown 引擎 —— 引擎是全局单例，下次还要用
        ttsManager.stop()
    }
}
