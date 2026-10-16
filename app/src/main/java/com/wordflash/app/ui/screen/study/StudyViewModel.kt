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
import kotlinx.coroutines.delay
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

        /**
         * 拼写题答错后停留多久（毫秒）。
         *
         * 比选择题长得多 —— 用户要看清「你写的是 / 正确的是」两行字，
         * 700ms 根本来不及读。
         */
        const val SPELLING_WRONG_DELAY_MS = 2200L

        /** 拼写题答对后停留多久（毫秒）。答对不需要细看，短一些更流畅 */
        const val SPELLING_CORRECT_DELAY_MS = 700L
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
     * [selectedOption] 属于哪一道题。
     *
     * UI 必须拿它和当前题目比对：只有 id 相同才显示高亮。
     * 详见 [selectOption] 里的说明。
     */
    private val _selectedOptionWordId = MutableStateFlow<Long?>(null)
    val selectedOptionWordId: StateFlow<Long?> = _selectedOptionWordId.asStateFlow()

    /**
     * 拼写模式上一次作答的对错反馈。
     *
     * null 表示当前题目还没作答（或已翻到下一题）。
     * 之前拼写模式只把 `isCorrect` 交给 [StudySessionManager] 就完事，
     * UI 完全不知道对错 —— 用户拼错了也毫无提示，只能自己猜。
     */
    private val _spellingFeedback = MutableStateFlow<SpellingFeedback?>(null)
    val spellingFeedback: StateFlow<SpellingFeedback?> = _spellingFeedback.asStateFlow()

    /** 选择题 / 拼写题作答后的对错反馈 */
    data class SpellingFeedback(
        /** 用户输入的原样内容，用于回显「你写的是 xxx」 */
        val input: String,
        /** 正确答案 */
        val expected: String,
        val isCorrect: Boolean
    )

    /**
     * 是否正在显示上一题的作答反馈。
     *
     * 反馈展示期间要**挡住新的自动播放与新的作答**，
     * 否则「答完立刻跳下一题」会让用户看不清对错，
     * 也会让下一题的发音盖掉反馈。
     */
    private val _isShowingFeedback = MutableStateFlow(false)
    val isShowingFeedback: StateFlow<Boolean> = _isShowingFeedback.asStateFlow()

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
        _currentMode.value = mode
        resetFeedback()

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
        _currentMode.value = mode
        resetFeedback()

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

        // 关键：先记下当前题目 id，反馈只对这一题有效。
        //
        // [StudySessionManager.answer] 会**立刻**把 currentIndex 推到下一题，
        // 而选项高亮还要停留一会儿让用户看清对错。
        // 如果不绑定题目 id，这 700ms 内渲染的已经是下一题，
        // 却带着上一题的高亮 —— 等于把下一题的答案直接标出来了。
        val answeredWordId = sessionState.value.currentCard?.wordId
        _selectedOption.value = option
        _selectedOptionWordId.value = answeredWordId

        val isCorrect = option == correctAnswer
        if (isCorrect && speakOnCorrect) {
            sessionState.value.currentCard?.let { speak(it.spelling) }
        }

        viewModelScope.launch {
            sessionManager.answer(isCorrect)
            // 延迟让用户看清对错，再清空高亮
            delay(ANSWER_FEEDBACK_DELAY_MS)
            _selectedOption.value = null
            _selectedOptionWordId.value = null
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
     *
     * 与以前的区别：现在会把对错结果暴露给 UI（[spellingFeedback]），
     * 答错时停留在本题展示正确答案，而不是无声无息地翻到下一题。
     * 用户点了「下一题」或等够时间才继续。
     */
    fun submitSpelling(input: String, expected: String) {
        // 已经提交过就别重复记账了（用户可能连点提交）
        if (_spellingFeedback.value != null) return

        val isCorrect = input.trim().equals(expected.trim(), ignoreCase = true)

        _spellingFeedback.value = SpellingFeedback(
            input = input.trim(),
            expected = expected.trim(),
            isCorrect = isCorrect
        )
        _isShowingFeedback.value = true

        viewModelScope.launch {
            sessionManager.answer(isCorrect)
        }
    }

    /**
     * 拼写题反馈结束后进入下一题。
     *
     * 由 UI 在两种情况调用：用户点「下一题」，或倒计时结束自动继续。
     */
    fun advanceFromSpelling() {
        if (_spellingFeedback.value == null) return
        _spellingFeedback.value = null
        _isShowingFeedback.value = false
        maybeAutoPlayNext()
    }

    // ============================================================
    // 发音
    // ============================================================

    /** 清掉上一轮遗留的作答反馈，避免新会话第一题就带着旧的对错标记 */
    private fun resetFeedback() {
        _selectedOption.value = null
        _selectedOptionWordId.value = null
        _spellingFeedback.value = null
        _isShowingFeedback.value = false
    }

    fun speak(text: String) {
        ttsManager.speak(text)
    }

    /** 朗读当前单词 */
    fun speakCurrent() {
        sessionState.value.currentCard?.let { ttsManager.speak(it.spelling) }
    }

    fun stopSpeaking() = ttsManager.stop()

    /** 当前会话的学习模式，用于判断哪些自动行为需要禁用 */
    private val _currentMode = MutableStateFlow(StudyMode.FLASHCARD)

    /**
     * 答完一题后自动朗读下一题。
     *
     * **听音辨词模式必须排除** —— 那个模式的题目本身就是发音，
     * 提前播放等于把下一题的答案直接念给用户听。
     * 这是「完成之后会自动给出下一个题的答案」的根因。
     *
     * 听音辨词由 ListeningQuestion 自己在题目进入时播放，时机才对。
     */
    private fun maybeAutoPlayNext() {
        if (!_autoPlay.value) return
        if (_currentMode.value == StudyMode.LISTENING) return
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
        resetFeedback()
        _bookName.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        // 只停发音，不 shutdown 引擎 —— 引擎是全局单例，下次还要用
        ttsManager.stop()
    }
}
