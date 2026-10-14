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
import com.wordflash.app.ui.screen.booklist.BookListScreen
import com.wordflash.app.ui.screen.createbook.CreateBookScreen
import com.wordflash.app.ui.screen.home.HomeScreen
import com.wordflash.app.ui.screen.result.ResultScreen
import com.wordflash.app.ui.screen.settings.SettingsScreen
import com.wordflash.app.ui.screen.stats.StatsScreen
import com.wordflash.app.ui.screen.study.ModeSelectScreen
import com.wordflash.app.ui.screen.study.StudyScreen
import com.wordflash.app.ui.screen.wordbook.WordbookScreen

/**
 * 搴旂敤涓诲鑸€? *
 * 搴曢儴瀵艰埅閲囩敤銆屽洖閫€鍒拌捣濮嬬洰鐨勫湴 + 鐘舵€佹仮澶嶃€嶇殑鏍囧噯妯″紡锛? * 鍙嶅鐐瑰嚮鍚屼竴 tab 涓嶄細鍫嗙Н鍥為€€鏍堬紝鍒囪蛋鍐嶅垏鍥炶兘淇濈暀婊氬姩浣嶇疆銆? *
 * 瀛︿範椤典笌缁撴灉椤?*涓嶆樉绀哄簳閮ㄥ鑸爮** 鈥斺€?瀛︿範鏃堕渶瑕佷笓娉紝
 * 涓斾腑閫斿垏 tab 浼氭墦鏂細璇濄€? */
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
            // ---------- 搴曢儴瀵艰埅鐨勫洓涓富椤甸潰 ----------
            composable(Routes.HOME) { HomeScreen(navController) }
            composable(Routes.BOOK_LIST) { BookListScreen(navController) }
            composable(Routes.STATS) { StatsScreen(navController) }
            composable(Routes.WORDBOOK) { WordbookScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.CREATE_BOOK) { CreateBookScreen(navController) }

            // ---------- 閿欓涓撻」缁冧範 ----------
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

            // ---------- 妯″紡閫夋嫨 ----------
            composable(
                route = Routes.MODE_SELECT,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.LongType }
                )
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: 0L
                ModeSelectScreen(bookId = bookId, navController = navController)
            }

            // ---------- 瀛︿範椤?----------
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
                        // 缁撴灉椤电敤 replace 鑰岄潪 navigate锛?                        // 瀛﹀畬鍚庢寜杩斿洖閿簲璇ュ洖鍒版ā寮忛€夋嫨锛岃€屼笉鏄噸鏂拌繘鍏ュ涔犻〉
                        navController.navigate(Routes.result(total, correct, durationMs)) {
                            popUpTo(Routes.STUDY) { inclusive = true }
                        }
                    }
                )
            }

            // ---------- 缁撴灉椤?----------
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
                        // 鍥炲埌涓婁竴灞傦紙妯″紡閫夋嫨锛夛紝璁╃敤鎴烽噸鏂伴€夋ā寮忓啀瀛?                        navController.popBackStack()
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
