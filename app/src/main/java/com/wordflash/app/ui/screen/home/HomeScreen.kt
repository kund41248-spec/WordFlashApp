package com.wordflash.app.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wordflash.app.ui.components.StatChip
import com.wordflash.app.ui.navigation.Routes
import com.wordflash.app.util.DateUtils

/**
 * 首页：今日学习概览 + 快速开始。
 *
 * 顶部显示「待复习 / 今日已学 / 连续打卡」，中间是开始按钮，
 * 下面给出最近使用的词库快捷入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("单词速记") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // ---------- 今日概览 ----------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "今日学习",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatChip(value = "${uiState.dueCount}", label = "待复习")
                        StatChip(value = "${uiState.todayLearned}", label = "今日新学")
                        StatChip(value = "${uiState.streak}", label = "连续打卡")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (uiState.todayDurationMs > 0) {
                            "今日已学 ${DateUtils.formatDuration(uiState.todayDurationMs)}"
                        } else {
                            "今天还没有开始学习"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---------- 开始学习 ----------
            if (uiState.defaultBookId != null) {
                Button(
                    onClick = {
                        navController.navigate(Routes.study(uiState.defaultBookId!!, "flashcard"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(
                        text = if (uiState.dueCount > 0) "开始复习（${uiState.dueCount}）" else "开始学习",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { navController.navigate(Routes.BOOK_LIST) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("选择词库")
                }
            } else {
                Text(
                    text = "词库正在导入中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---------- 快捷入口 ----------
            SectionTitle("快捷入口")
            Spacer(Modifier.height(8.dp))

            QuickEntry("生词本", "答错的词会自动收进这里") {
                navController.navigate(Routes.WORDBOOK)
            }
            Spacer(Modifier.height(8.dp))
            QuickEntry("学习统计", "查看正确率、时长与打卡记录") {
                navController.navigate(Routes.STATS)
            }
            Spacer(Modifier.height(8.dp))
            QuickEntry("设置", "深色模式、发音语速、数据管理") {
                navController.navigate(Routes.SETTINGS)
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun QuickEntry(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
