package com.wordflash.app.ui.screen.addwords

import com.wordflash.app.data.seed.DictWordDto

/**
 * 录入草稿。
 *
 * 与新建词库页的 DraftWord 结构一致，但**刻意分成两个独立的类**：
 * 两页的校验规则和字段以后可能分叉（比如追加时允许只填单词、
 * 释义留空待补），共用一份 data class 会让这种分叉变得别扭。
 */
data class DraftWord(
    val spelling: String,
    val meaning: String,
    val phonetic: String = "",
    val example: String = ""
) {
    fun toDto(): DictWordDto = DictWordDto(
        spelling = spelling,
        phonetic = phonetic,
        meaning = meaning,
        example = example
    )
}
