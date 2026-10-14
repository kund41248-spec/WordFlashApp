package com.wordflash.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 全局路由表�? *
 * 学习页与结果页不放入底部导航，通过路由参数携带会话信息�? * 避免在导航层持有可变状态�? */
object Routes {
    const val HOME = "home"
    const val BOOK_LIST = "book_list"
    const val STATS = "stats"
    const val WORDBOOK = "wordbook"
    const val SETTINGS = "settings"
    const val CREATE_BOOK = "create_book"

    /** 学习页：需要词�?id 与学习模�?*/
    const val STUDY = "study/{bookId}/{mode}"
    fun study(bookId: Long, mode: String) = "study/$bookId/$mode"

    /**
     * 错题专项练习�?     *
     * 不带参数 —�?错题列表由学习页自己查库�?     * 走导航参数传一长串 wordId 既冗长又容易超出路由长度限制�?     */
    const val PRACTICE_WRONG = "practice_wrong/{mode}"
    fun practiceWrong(mode: String = "flashcard") = "practice_wrong/$mode"

    /** 模式选择页：需要词�?id */
    const val MODE_SELECT = "mode_select/{bookId}"
    fun modeSelect(bookId: Long) = "mode_select/$bookId"

    /** 结果页：需要本次会话的统计摘要 */
    const val RESULT = "result/{total}/{correct}/{durationMs}"
    fun result(total: Int, correct: Int, durationMs: Long) =
        "result/$total/$correct/$durationMs"

    /** 词库详情（查看某词库下的单词列表�?*/
    const val BOOK_DETAIL = "book_detail/{bookId}"
    fun bookDetail(bookId: Long) = "book_detail/$bookId"
}

/**
 * 底部导航项定义�? */
data class BottomNavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME, com.wordflash.app.R.string.nav_home, Icons.Filled.Home),
    BottomNavItem(Routes.BOOK_LIST, com.wordflash.app.R.string.nav_wordbook, Icons.Filled.MenuBook),
    BottomNavItem(Routes.STATS, com.wordflash.app.R.string.nav_stats, Icons.Filled.BarChart),
    BottomNavItem(Routes.SETTINGS, com.wordflash.app.R.string.nav_settings, Icons.Filled.Settings)
)
