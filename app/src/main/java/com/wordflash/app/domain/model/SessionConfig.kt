package com.wordflash.app.domain.model

/**
 * 一次学习会话的配置。
 */
data class SessionConfig(
    /**
     * 所属词库。
     *
     * 当 [wordIds] 不为空时此字段仅作记录用途（结果页可能展示来源），
     * 实际组卷以 [wordIds] 为准。
     */
    val bookId: Long = 0L,

    /** 学习模式 */
    val mode: StudyMode,

    /** 本次最多复习多少个到期词 */
    val reviewLimit: Int = 20,

    /** 本次最多引入多少个新词。0 表示只复习 */
    val newWordLimit: Int = 10,

    /**
     * 指定词表组卷。
     *
     * 非空时不按「词库 + 到期时间」取词，而是**直接使用这批单词** ——
     * 用于「练习错题」这类"只练指定词"的场景。
     *
     * 为 null 表示正常的按词库学习。
     */
    val wordIds: List<Long>? = null,

    /**
     * 答错的词是否在本轮末尾重现。
     *
     * 开启时答错的词会被追加到队尾再练一次 —— 这比"过 10 分钟再来"
     * 更符合单次学习场景（用户可能学完就退出 APP）。
     */
    val repeatWrongAtEnd: Boolean = true,

    /** 是否自动播放发音 */
    val autoPlayAudio: Boolean = false
) {
    /** 是否是「指定词表」的专项练习 */
    val isCustomWordList: Boolean get() = !wordIds.isNullOrEmpty()
}
