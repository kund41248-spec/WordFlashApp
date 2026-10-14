package com.wordflash.app.domain.model

import com.wordflash.app.data.local.entity.Word
import com.wordflash.app.data.local.entity.WordProgress

/**
 * 学习卡片。
 *
 * 这是 UI 层渲染一道题所需的**全部数据**，由 [com.wordflash.app.domain.session.StudySessionManager]
 * 组装。把「取数据」和「渲染」分开，好处是 UI 完全无状态，
 * 换学习模式时只需换组装逻辑，Composable 不用改。
 */
data class StudyCard(
    /** 单词 id */
    val wordId: Long,

    /** 拼写 */
    val spelling: String,

    /** 音标 */
    val phonetic: String,

    /** 释义 */
    val meaning: String,

    /** 词性 */
    val partOfSpeech: String,

    /** 例句 */
    val example: String,

    /** 例句翻译 */
    val exampleTranslation: String,

    /** 自定义音频；为空则用 TTS */
    val audioUrl: String,

    /**
     * 选择题的干扰项释义（含正确答案，已打乱）。
     * 仅 [StudyMode.FILL_BLANK] 使用。
     */
    val meaningOptions: List<String> = emptyList(),

    /**
     * 选择题的干扰项拼写（含正确答案，已打乱）。
     * 仅 [StudyMode.LISTENING] 使用。
     */
    val spellingOptions: List<String> = emptyList(),

    /** 该词此前是否学过。用于统计时区分「新学」与「复习」 */
    val isNew: Boolean
) {
    companion object {
        /**
         * 从数据库对象组装卡片。
         *
         * @param distractors 干扰项词列表；不足时选项会少于 4 个，UI 需自适应
         */
        fun from(
            word: Word,
            progress: WordProgress?,
            mode: StudyMode,
            distractors: List<Word>
        ): StudyCard {
            val meaningOptions = if (mode == StudyMode.FILL_BLANK) {
                buildOptions(
                    correct = word.meaning,
                    pool = distractors.map { it.meaning },
                    optionCount = 4
                )
            } else emptyList()

            val spellingOptions = if (mode == StudyMode.LISTENING) {
                buildOptions(
                    correct = word.spelling,
                    pool = distractors.map { it.spelling },
                    optionCount = 4
                )
            } else emptyList()

            return StudyCard(
                wordId = word.id,
                spelling = word.spelling,
                phonetic = word.phonetic,
                meaning = word.meaning,
                partOfSpeech = word.partOfSpeech,
                example = word.example,
                exampleTranslation = word.exampleTranslation,
                audioUrl = word.audioUrl,
                meaningOptions = meaningOptions,
                spellingOptions = spellingOptions,
                isNew = progress == null
            )
        }

        /**
         * 构造选项：正确答案 + 去重后的干扰项，打乱顺序。
         *
         * 干扰项若与正确答案文本相同会被剔除（同一释义对应多个词时会出现），
         * 否则一道题会有两个正确选项。
         */
        private fun buildOptions(
            correct: String,
            pool: List<String>,
            optionCount: Int
        ): List<String> {
            val options = LinkedHashSet<String>()
            options += correct
            pool.asSequence()
                .filter { it.isNotBlank() && it != correct }
                .forEach { if (options.size < optionCount) options += it }
            return options.toList().shuffled()
        }
    }
}
