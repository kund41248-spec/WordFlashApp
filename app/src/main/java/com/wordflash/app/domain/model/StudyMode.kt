package com.wordflash.app.domain.model

/**
 * 学习模式。
 *
 * [key] 会写入数据库（StudyRecord.mode），因此**不可随意改名**，
 * 否则历史记录的模式统计会错乱。如需展示名称，用 [displayName]。
 */
enum class StudyMode(
    val key: String,
    val displayName: String,
    val description: String
) {
    FLASHCARD(
        key = "flashcard",
        displayName = "单词卡片",
        description = "看单词回忆释义，翻面核对"
    ),
    FILL_BLANK(
        key = "fill_blank",
        displayName = "选词填空",
        description = "从四个选项中选出正确释义"
    ),
    SPELLING(
        key = "spelling",
        displayName = "拼写默写",
        description = "根据释义和发音拼出单词"
    ),
    LISTENING(
        key = "listening",
        displayName = "听音辨词",
        description = "听发音，从选项中选出对应单词"
    );

    companion object {
        fun fromKey(key: String): StudyMode =
            entries.firstOrNull { it.key == key } ?: FLASHCARD
    }
}
