package com.wordflash.app.domain.model

/**
 * 一次学习会话结束后的统计摘要。
 *
 * 由 [com.wordflash.app.domain.session.StudySessionManager] 在会话结束时产出，
 * 传给结果页展示。
 */
data class SessionSummary(
    /** 总题数（含重复出现的词） */
    val totalQuestions: Int,

    /** 答对题数 */
    val correctCount: Int,

    /** 新学单词数（去重） */
    val learnedNewCount: Int,

    /** 复习单词数（去重） */
    val reviewedCount: Int,

    /** 本次会话总时长（毫秒） */
    val durationMs: Long,

    /** 答错的单词 id，用于结果页「查看错词」 */
    val wrongWordIds: List<Long> = emptyList()
) {
    /** 正确率 0f~1f */
    val accuracy: Float
        get() = if (totalQuestions == 0) 0f else correctCount.toFloat() / totalQuestions

    /** 正确率百分比整数，用于环形图 */
    val accuracyPercent: Int
        get() = (accuracy * 100).toInt()

    /** 平均每题耗时（毫秒） */
    val averageMsPerQuestion: Long
        get() = if (totalQuestions == 0) 0L else durationMs / totalQuestions
}
