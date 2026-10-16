package com.wordflash.app.domain.session

import com.wordflash.app.data.repository.StatsRepository
import com.wordflash.app.data.repository.StudyRepository
import com.wordflash.app.data.repository.WordRepository
import com.wordflash.app.domain.model.SessionConfig
import com.wordflash.app.domain.model.SessionSummary
import com.wordflash.app.domain.model.StudyCard
import com.wordflash.app.domain.model.WordWithProgress
import com.wordflash.app.domain.scheduler.EbbinghausScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学习会话状态机。
 *
 * ## 职责划分
 *
 * - **本类**：管理「当前第几题、队列怎么走、答对答错后做什么」
 * - [EbbinghausScheduler]：只负责算下次复习时间（纯函数）
 * - Repository：只负责存取
 *
 * 三者互不越界，因此调度算法可以单独测试，会话流程也可以脱离数据库测试。
 *
 * ## 为什么用 StateFlow 而不是 LiveData
 *
 * Compose 原生消费 StateFlow，不需要 `observeAsState()` 适配层；
 * 且 StateFlow 支持 `value` 直接读，便于在协程里做同步判断。
 */
@Singleton
class StudySessionManager @Inject constructor(
    private val studyRepository: StudyRepository,
    private val wordRepository: WordRepository,
    private val statsRepository: StatsRepository
) {

    /** 会话运行时状态 */
    data class State(
        val isLoading: Boolean = false,
        val cards: List<StudyCard> = emptyList(),
        val currentIndex: Int = 0,
        val isFlipped: Boolean = false,
        val correctCount: Int = 0,
        val answeredCount: Int = 0,
        val isFinished: Boolean = false,
        val summary: SessionSummary? = null,
        val error: String? = null
    ) {
        val currentCard: StudyCard? get() = cards.getOrNull(currentIndex)
        val progress: Float
            get() = if (cards.isEmpty()) 0f else (currentIndex.toFloat() / cards.size)
        val remaining: Int get() = (cards.size - currentIndex).coerceAtLeast(0)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    // ---- 会话内部记账（不暴露给 UI，避免 Compose 重组时被误读）----

    private var config: SessionConfig? = null
    private var startedAt: Long = 0
    private var questionShownAt: Long = 0

    /** 已出现过的单词 id，用于去重统计「新学」与「复习」 */
    private val seenWordIds = linkedSetOf<Long>()

    /** 首次出现的词（新学） */
    private val newWordIds = linkedSetOf<Long>()

    /** 答错的词，用于结果页与队尾重现 */
    private val wrongWordIds = linkedSetOf<Long>()

    /** 是否已经写过一次进度（避免同一词多题重复累加 learnedCount） */
    private val progressWritten = hashSetOf<Long>()

    /** 每个词在本轮被重现的次数，用于给队列增长设上限 */
    private val repeatCount = hashMapOf<Long, Int>()

    /**
     * 每个词当前连续答对了几次，用于判断能否移出错题本。
     *
     * 只存在内存里，不落库：跨会话的连续性是靠 [WordProgress.repetitions]
     * 维护的，这里统计的是**本轮会话内**的连续性 ——
     * 一轮里连对两次，才认为这次是真的记住了。
     */
    private val currentStreak = hashMapOf<Long, Int>()

    // ============================================================
    // 会话生命周期
    // ============================================================

    /**
     * 开始一次会话。
     *
     * 会组装学习队列并预取每道题的干扰项。干扰项在这里一次性取好，
     * 而不是每次滑动时再查库 —— 避免答题过程中出现卡顿。
     */
    suspend fun start(config: SessionConfig, now: Long = System.currentTimeMillis()) {
        this.config = config
        this.startedAt = now
        reset()

        _state.value = State(isLoading = true)

        runCatching {
            // 两条组卷路径：指定词表（练习错题）优先于按词库取词
            val queue = if (config.isCustomWordList) {
                studyRepository.buildSessionFromWords(
                    wordIds = config.wordIds.orEmpty(),
                    limit = config.reviewLimit
                )
            } else {
                studyRepository.buildSession(
                    bookId = config.bookId,
                    now = now,
                    reviewLimit = config.reviewLimit,
                    newWordLimit = config.newWordLimit
                )
            }
            if (queue.isEmpty()) {
                _state.value = State(isLoading = false, isFinished = true)
                return
            }
            buildCards(queue, config)
        }.onFailure { e ->
            _state.value = State(isLoading = false, error = e.message ?: "加载失败")
        }

        questionShownAt = System.currentTimeMillis()
    }

    private suspend fun buildCards(queue: List<WordWithProgress>, config: SessionConfig) {
        val cards = queue.map { item ->
            val word = item.word
            // 只有选择题模式才需要干扰项，避免无谓查询
            val distractors = when (config.mode) {
                com.wordflash.app.domain.model.StudyMode.FILL_BLANK,
                com.wordflash.app.domain.model.StudyMode.LISTENING ->
                    wordRepository.getDistractors(word.bookId, word.id, DISTRACTOR_COUNT)
                else -> emptyList()
            }
            StudyCard.from(word, item.progress, config.mode, distractors)
        }

        cards.forEach { card ->
            seenWordIds += card.wordId
            if (card.isNew) newWordIds += card.wordId
        }

        _state.value = State(
            isLoading = false,
            cards = cards,
            currentIndex = 0,
            correctCount = 0,
            answeredCount = 0
        )
    }

    private fun reset() {
        seenWordIds.clear()
        newWordIds.clear()
        wrongWordIds.clear()
        progressWritten.clear()
        repeatCount.clear()
        currentStreak.clear()
        _state.value = State()
    }

    // ============================================================
    // 答题交互
    // ============================================================

    /** 卡片翻面（单词卡片模式） */
    fun flip() {
        _state.value = _state.value.let { it.copy(isFlipped = !it.isFlipped) }
    }

    /**
     * 提交一次作答。
     *
     * 这是会话的核心：一次调用同时完成
     * 「写进度 → 记统计 → 更新会话状态 → 决定下一题」。
     *
     * @param isCorrect 是否答对
     * @param now 当前时间，便于测试
     */
    suspend fun answer(isCorrect: Boolean, now: Long = System.currentTimeMillis()) {
        val current = _state.value
        val card = current.currentCard ?: return

        val durationMs = (now - questionShownAt).coerceAtLeast(0)
        val cfg = config

        // ---------- 1. 更新艾宾浩斯进度 ----------
        val existing = studyRepository.getProgress(card.wordId)
        val isFirstTime = existing == null
        var updated = EbbinghausScheduler.schedule(existing, isCorrect, now)
            .copy(wordId = card.wordId)

        // ---------- 2. 维护错题本 ----------
        //
        // 分工：错题本**自动**记录答错的词；生词本只由用户手动添加。
        // 因此这里不再往生词本塞东西，避免两个列表内容重复。
        //
        // 答对**连续 [WRONG_BOOK_GRADUATE_STREAK] 次**才移出错题本。
        //
        // 为什么不是答对一次就移出：一次答对可能只是蒙对或刚看过答案，
        // 立刻移出会让错题本「漏掉」其实还没记住的词，
        // 用户第二天再遇到它时又会答错，来回折腾。
        // 连续答对才说明真的记住了 —— 这与市面错题本的通行做法一致。
        updated = if (isCorrect) {
            // 答对：累计连续答对次数，达标才移出。
            // 注意 wrongCount 是只增不减的统计值，不在这里改动。
            val streak = currentStreak.getOrDefault(card.wordId, 0) + 1
            currentStreak[card.wordId] = streak
            if (updated.isInWrongBook && streak >= WRONG_BOOK_GRADUATE_STREAK) {
                currentStreak.remove(card.wordId)
                updated.copy(isInWrongBook = false)
            } else {
                updated
            }
        } else {
            // 答错：收进错题本，并刷新「最近答错时间」用于排序；
            // 连续答对计数清零，从头再攒
            currentStreak[card.wordId] = 0
            wrongWordIds += card.wordId
            updated.copy(isInWrongBook = true, lastWrongAt = now)
        }
        studyRepository.saveProgress(updated)

        // ---------- 3. 写统计 ----------
        // learnedCount 只在该词首次作答时计一次，
        // 否则同一个词在一轮里答错后重现会被重复计入"新学"
        val countAsNew = isFirstTime && !progressWritten.contains(card.wordId)
        progressWritten += card.wordId

        statsRepository.recordAnswer(
            wordId = card.wordId,
            mode = cfg?.mode?.key ?: "flashcard",
            isCorrect = isCorrect,
            durationMs = durationMs,
            isNewLearned = countAsNew,
            timestamp = now
        )

        // ---------- 4. 更新会话状态 ----------
        val newCorrect = current.correctCount + if (isCorrect) 1 else 0
        val newAnswered = current.answeredCount + 1
        var cards = current.cards

        // 答错的词追加到队尾重现（可选）。
        //
        // 必须有重现次数上限：否则用户一直答错，队列会无限增长，
        // 会话永远结束不了。同一个词最多重现 MAX_REPEAT_PER_WORD 次。
        if (!isCorrect && cfg?.repeatWrongAtEnd == true) {
            val repeatTimes = repeatCount[card.wordId] ?: 0
            if (repeatTimes < MAX_REPEAT_PER_WORD) {
                repeatCount[card.wordId] = repeatTimes + 1
                cards = cards + card
            }
        }

        val nextIndex = current.currentIndex + 1
        val finished = nextIndex >= cards.size

        _state.value = current.copy(
            cards = cards,
            currentIndex = nextIndex,
            isFlipped = false,
            correctCount = newCorrect,
            answeredCount = newAnswered,
            isFinished = finished,
            summary = if (finished) buildSummary(cards.size, newCorrect, now) else null
        )

        questionShownAt = now
    }

    /** 「不认识 / 跳过」—— 等价于答错，但语义更贴近卡片模式 */
    suspend fun markUnknown(now: Long = System.currentTimeMillis()) = answer(false, now)

    /** 「认识」—— 等价于答对 */
    suspend fun markKnown(now: Long = System.currentTimeMillis()) = answer(true, now)

    private fun buildSummary(
        totalQuestions: Int,
        correctCount: Int,
        now: Long
    ): SessionSummary = SessionSummary(
        totalQuestions = totalQuestions,
        correctCount = correctCount,
        // 新学 = 首次出现的新词数；复习 = 出现过但不是新词的数量
        learnedNewCount = newWordIds.size,
        reviewedCount = (seenWordIds.size - newWordIds.size).coerceAtLeast(0),
        durationMs = (now - startedAt).coerceAtLeast(0),
        wrongWordIds = wrongWordIds.toList()
    )

    /**
     * 中途退出时的收尾。
     *
     * 进度已经在上面的 [answer] 里逐题落库了，所以这里不需要"保存"，
     * 只把状态标记为结束，避免 UI 继续等待。
     */
    fun abandon() {
        _state.value = _state.value.copy(isFinished = true)
    }

    /** 结果页返回后重置，避免下次进入时残留上次的卡片 */
    fun clear() {
        reset()
        config = null
    }

    private companion object {
        /** 选择题干扰项个数（不含正确答案） */
        const val DISTRACTOR_COUNT = 3

        /** 同一个词在一轮会话里最多重现几次，防止队列无限增长 */
        const val MAX_REPEAT_PER_WORD = 2

        /**
         * 连续答对几次才把单词移出错题本。
         *
         * 取 2 而不是 1：答对一次可能是蒙的或刚看过答案，
         * 连对两次才足以说明记住了。也不宜更高 ——
         * 错题本里的词通常已经会了，要求连对三次以上
         * 会让用户觉得「怎么老是移不出去」。
         */
        const val WRONG_BOOK_GRADUATE_STREAK = 2
    }
}
