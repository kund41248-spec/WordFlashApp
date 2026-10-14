package com.wordflash.app.domain.scheduler

import com.wordflash.app.data.local.entity.WordProgress
import com.wordflash.app.data.local.entity.WordState

/**
 * 艾宾浩斯遗忘曲线调度器（SM-2 简化变体）。
 *
 * ## 为什么用这个算法
 *
 * 纯艾宾浩斯曲线只描述「遗忘速度」，不解决「每个词该怎么排」。
 * SuperMemo 的 SM-2 在其基础上引入了两个关键量：
 *
 * - **间隔序列**：答对次数越多，下次复习间隔越长（1→2→4→7→15→30→60→180 天）
 * - **难度系数 EF**：每个词独立维护 1.3~2.8，答错扣、答对加
 *
 * 只有间隔序列没有 EF，会让「一遍就记住的词」和「反复忘的词」
 * 走同样的节奏；EF 让难词复习更频繁、易词拉长间隔。
 *
 * ## 状态机
 *
 * ```
 * NEW ──首次作答──► LEARNING ──连续答对 2 次──► REVIEWING ──间隔≥阈值──► MASTERED
 *                      ▲                            │
 *                      └──────────答错───────────────┘
 * ```
 *
 * ## 纯函数设计
 *
 * [schedule] 不读数据库、不取系统时间（[now] 由调用方传入），
 * 因此可以用固定时间戳做单元测试，行为完全可预测。
 */
object EbbinghausScheduler {

    /**
     * 复习间隔序列（天）。
     *
     * 索引 = 连续答对次数。取值参考艾宾浩斯遗忘曲线的关键节点
     * （20 分钟、1 小时、9 小时、1 天、2 天、6 天、31 天）并做工程化压缩：
     * 太短的间隔对移动端无意义（用户不会 20 分钟后再打开 APP），
     * 因此第一天内不重复出题，从 1 天起步。
     */
    val INTERVALS_DAYS = intArrayOf(0, 1, 2, 4, 7, 15, 30, 60, 180)

    /** 难度系数上下限 */
    const val MIN_EASE = 1.3f
    const val MAX_EASE = 2.8f
    const val DEFAULT_EASE = 2.5f

    /** 连续答对达到该次数后，从 LEARNING 升到 REVIEWING */
    const val REPETITIONS_TO_REVIEWING = 2

    /** 间隔达到该天数即视为已掌握 */
    const val MASTERED_INTERVAL_DAYS = 60

    /** 答错后多久重新出现（当天内再练一遍） */
    const val RELEARN_DELAY_MS = 10 * 60 * 1000L   // 10 分钟

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * 核心调度：根据本次作答结果计算新的学习进度。
     *
     * @param current 当前进度；`null` 表示这是一个从未学过的新词
     * @param isCorrect 本次是否答对
     * @param now 当前时间戳（毫秒），由调用方传入以便测试
     * @return 更新后的进度（可直接写回数据库）
     */
    fun schedule(
        current: WordProgress?,
        isCorrect: Boolean,
        now: Long
    ): WordProgress {
        val base = current ?: WordProgress(wordId = 0L, state = WordState.NEW)

        return if (isCorrect) {
            onCorrect(base, now)
        } else {
            onWrong(base, now)
        }
    }

    /**
     * 答对：连续次数 +1，间隔按序列前进，EF 小幅提升。
     */
    private fun onCorrect(p: WordProgress, now: Long): WordProgress {
        val repetitions = p.repetitions + 1
        val intervalIndex = repetitions.coerceAtMost(INTERVALS_DAYS.lastIndex)
        val baseInterval = INTERVALS_DAYS[intervalIndex]

        // EF 随答对缓慢上升，但设上限，避免简单词间隔无限膨胀
        val newEase = (p.easeFactor + 0.1f).coerceAtMost(MAX_EASE)

        // 实际间隔 = 基础间隔 × (EF / 默认EF)。
        //
        // 这里必须 **封顶在序列值**，否则会退化成"双重增长"：
        // 序列本身已经体现了难度递进（1→2→4→7→15→30→60→180），
        // 再乘 EF 会让第 8 次答对变成 180×2.8/2.5 = 201 天，
        // 既超出预期的上限，也让"已掌握"的判定与真实间隔脱节。
        //
        // 封顶后 EF 只起到"提前/延后一格"的作用：
        // 难词（EF 低）拿不满基础间隔，易词（EF 高）最多拿到基础间隔。
        val scaled = (baseInterval * newEase / DEFAULT_EASE).toInt()
        val effectiveDays = scaled.coerceIn(1, baseInterval)

        val newState = when {
            effectiveDays >= MASTERED_INTERVAL_DAYS -> WordState.MASTERED
            repetitions >= REPETITIONS_TO_REVIEWING -> WordState.REVIEWING
            else -> WordState.LEARNING
        }

        return p.copy(
            state = newState,
            repetitions = repetitions,
            intervalDays = effectiveDays,
            easeFactor = newEase,
            nextReviewAt = now + effectiveDays * DAY_MS,
            lastReviewAt = now,
            correctCount = p.correctCount + 1,
            reviewCount = p.reviewCount + 1
        )
    }

    /**
     * 答错：连续次数清零，EF 下降，间隔重置。
     *
     * 关键点：**答错不是简单地"回到起点"**。
     * EF 会保留并下调，因此这个词之后再答对时，间隔增长会比新词更慢——
     * 这正确地反映了「这个词对我来说比较难」。
     */
    private fun onWrong(p: WordProgress, now: Long): WordProgress {
        val newEase = (p.easeFactor - 0.2f).coerceAtLeast(MIN_EASE)

        return p.copy(
            state = WordState.LEARNING,
            repetitions = 0,
            intervalDays = 0,
            easeFactor = newEase,
            // 10 分钟后重新出现，当天内再练一次
            nextReviewAt = now + RELEARN_DELAY_MS,
            lastReviewAt = now,
            wrongCount = p.wrongCount + 1,
            reviewCount = p.reviewCount + 1
        )
    }

    /**
     * 距离下次复习还有多少天，用于 UI 显示「下次复习：3 天后」。
     */
    fun daysUntilNextReview(progress: WordProgress, now: Long): Int {
        if (progress.nextReviewAt <= now) return 0
        return ((progress.nextReviewAt - now) / DAY_MS).toInt()
    }

    /**
     * 判断某个词此刻是否到期，用于在单词列表上打「待复习」标记。
     */
    fun isDue(progress: WordProgress?, now: Long): Boolean {
        if (progress == null) return true          // 新词永远"到期"
        if (progress.state == WordState.MASTERED) return false
        return progress.nextReviewAt <= now
    }
}
