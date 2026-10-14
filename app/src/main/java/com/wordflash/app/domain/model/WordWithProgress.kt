package com.wordflash.app.domain.model

import androidx.room.Embedded
import com.wordflash.app.data.local.entity.Word
import com.wordflash.app.data.local.entity.WordProgress
import com.wordflash.app.data.local.entity.WordState

/**
 * 单词 + 其学习进度。
 *
 * 用于 DAO 的联表查询结果：学新词时进度为 null（还没学过），
 * 复习时进度一定存在。
 *
 * 这里用 [Embedded] + 前缀的方式把 JOIN 出来的扁平列映射回两个对象。
 * 前缀 `p_` 必须与 DAO 中 SELECT 的别名严格一致。
 */
data class WordWithProgress(
    @Embedded
    val word: Word,

    @Embedded(prefix = "p_")
    val progress: WordProgress?
) {
    /** 当前状态；未学过时视为 NEW */
    val state: Int
        get() = progress?.state ?: WordState.NEW

    /** 是否已进入学习流程 */
    val isLearned: Boolean
        get() = progress != null

    /**
     * 掌握程度 0f~1f，用于词库详情页的进度条。
     * 依据连续答对次数与难度系数估算：答对越多、间隔越长，越接近 1。
     */
    val mastery: Float
        get() {
            val p = progress ?: return 0f
            if (p.state == WordState.MASTERED) return 1f
            // repetitions 达到 5 次即视为接近掌握
            val byRepetition = (p.repetitions / 5f).coerceIn(0f, 1f)
            val byState = when (p.state) {
                WordState.LEARNING -> 0.3f
                WordState.REVIEWING -> 0.7f
                else -> 0f
            }
            return maxOf(byRepetition, byState)
        }
}
