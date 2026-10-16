package com.wordflash.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wordflash.app.ui.screen.addwords.AddWordsScreen
import com.wordflash.app.ui.screen.booklist.BookListScreen
import com.wordflash.app.ui.screen.createbook.CreateBookScreen
import com.wordflash.app.ui.screen.home.HomeScreen
import com.wordflash.app.ui.screen.result.ResultScreen
import com.wordflash.app.ui.screen.settings.SettingsScreen
import com.wordflash.app.ui.screen.stats.StatsScreen
import com.wordflash.app.ui.screen.study.ModeSelectScreen
import com.wordflash.app.ui.screen.study.StudyScreen
import com.wordflash.app.ui.screen.wordbook.WordbookScreen
import com.wordflash.app.ui.screen.wrongbooksearch.WrongBookSearchScreen

/**
 * 应用主导航。
 *
 * 底部导航采用「回退到起始目的地 + 状态恢复」的标准模式：
 * 反复点击同一 tab 不会堆积回退栈，切走再切回能保留滚动位置。
 *
 * 学习页与结果页**不显示底部导航栏** —— 学习时需要专注，
 * 且中途切 tab 会打断会话。
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = stringResource(item.labelRes)
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            // ---------- 底部导航的五个主页面 ----------
            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.BOOK_LIST) { BookListScreen(navController) }
            composable(Routes.STATS) { StatsScreen(navController) }
            composable(Routes.WORDBOOK) { WordbookScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.CREATE_BOOK) { CreateBookScreen(navController) }

            // ---------- 向已有词库追加单词 ----------
            composable(
                route = Routes.ADD_WORDS,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType }
                )
            ) {
                AddWordsScreen(navController)
            }

            // ---------- 搜索并加入错题本 ----------
            composable(Routes.SEARCH_TO_WRONG_BOOK) {
                WrongBookSearchScreen(navController)
            }

            // ---------- 错题专项练习 ----------
            composable(
                route = Routes.PRACTICE_WRONG,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType }
                )
            ) { entry ->
                val mode = entry.arguments?.getString("mode") ?: "flashcard"

                StudyScreen(
                    bookId = 0L,
                    modeKey = mode,
                    practiceWrong = true,
                    onBack = { navController.popBackStack() },
                    onFinished = { total, correct, durationMs ->
                        navController.navigate(Routes.result(total, correct, durationMs)) {
                            popUpTo(Routes.PRACTICE_WRONG) { inclusive = true }
                        }
                    }
                )
            }

            // ---------- 模式选择 ----------
            composable(
                route = Routes.MODE_SELECT,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType }
                )
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: 0L
                ModeSelectScreen(bookId = bookId, navController = navController)
            }

            // ---------- 学习页 ----------
            composable(
                route = Routes.STUDY,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType },
                    navArgument("mode") { type = NavType.StringType }
                )
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: 0L
                val mode = entry.arguments?.getString("mode") ?: "flashcard"

                StudyScreen(
                    bookId = bookId,
                    modeKey = mode,
                    onBack = { navController.popBackStack() },
                    onFinished = { total, correct, durationMs ->
                        // 结果页用 replace 而非 navigate：
                        // 学完后按返回键应该回到模式选择，而不是重新进入学习页
                        navController.navigate(Routes.result(total, correct, durationMs)) {
                            popUpTo(Routes.STUDY) { inclusive = true }
                        }
                    }
                )
            }

            // ---------- 结果页 ----------
            composable(
                route = Routes.RESULT,
                arguments = listOf(
                    navArgument("total") { type = NavType.IntType },
                    navArgument("correct") { type = NavType.IntType },
                    navArgument("durationMs") { type = NavType.LongType }
                )
            ) { entry ->
                val total = entry.arguments?.getInt("total") ?: 0
                val correct = entry.arguments?.getInt("correct") ?: 0
                val durationMs = entry.arguments?.getLong("durationMs") ?: 0L

                ResultScreen(
                    total = total,
                    correct = correct,
                    durationMs = durationMs,
                    onBackHome = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    },
                    onStudyAgain = {
                        // 回到上一层（模式选择），让用户重新选模式再学
                        navController.popBackStack()
                    },
                    onViewWordbook = {
                        navController.navigate(Routes.WORDBOOK) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}