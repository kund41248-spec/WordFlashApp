package com.wordflash.app.ui.screen.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wordflash.app.ui.theme.ThemeMode

/**
 * 设置页。
 *
 * 分四组：外观 / 发音 / 学习 / 数据，每组一张卡片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showClearDataDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("清空学习记录？") },
            text = {
                Text(
                    "将删除所有学习进度、打卡记录与统计数据。" +
                        "词库和单词本身会保留。此操作不可撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDataDialog = false
                    viewModel.clearStudyData()
                }) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // ==================== 外观 ====================
            SectionCard("外观") {
                SettingRow(
                    title = "主题模式",
                    subtitle = when (ui.themeMode) {
                        ThemeMode.SYSTEM -> "跟随系统"
                        ThemeMode.LIGHT -> "始终浅色"
                        ThemeMode.DARK -> "始终深色"
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                ThemeChip(
                                    label = when (mode) {
                                        ThemeMode.SYSTEM -> "系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    },
                                    selected = ui.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) }
                                )
                            }
                        }
                    }
                )

                SettingRow(
                    title = "动态取色",
                    subtitle = "跟随壁纸配色（Android 12+）",
                    trailing = {
                        Switch(
                            checked = ui.dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // ==================== 发音 ====================
            SectionCard("发音") {
                if (!ui.ttsAvailable) {
                    Text(
                        text = "当前设备不支持语音朗读",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("语速", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "%.2f×".format(ui.speechRate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { viewModel.previewSpeech() },
                            enabled = ui.ttsAvailable
                        ) {
                            Text("试听")
                        }
                    }
                    Slider(
                        value = ui.speechRate,
                        onValueChange = { viewModel.setSpeechRate(it) },
                        valueRange = 0.5f..1.5f,
                        steps = 9,
                        enabled = ui.ttsAvailable
                    )
                }

                SettingRow(
                    title = "自动播放发音",
                    subtitle = "进入题目时自动朗读",
                    trailing = {
                        Switch(
                            checked = ui.autoPlayAudio,
                            onCheckedChange = { viewModel.setAutoPlayAudio(it) }
                        )
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // ==================== 学习 ====================
            SectionCard("学习") {
                SettingRow(
                    title = "答错重现",
                    subtitle = "本轮答错的词在末尾再练一次",
                    trailing = {
                        Switch(
                            checked = ui.repeatWrongAtEnd,
                            onCheckedChange = { viewModel.setRepeatWrongAtEnd(it) }
                        )
                    }
                )

                NumberStepper(
                    title = "每组新词数",
                    value = ui.dailyNewLimit,
                    range = 5..50,
                    step = 5,
                    onValueChange = { viewModel.setDailyNewLimit(it) }
                )

                NumberStepper(
                    title = "每组复习数",
                    value = ui.dailyReviewLimit,
                    range = 5..100,
                    step = 5,
                    onValueChange = { viewModel.setDailyReviewLimit(it) }
                )
            }

            Spacer(Modifier.height(12.dp))

            // ==================== 数据 ====================
            SectionCard("数据") {
                SettingRow(
                    title = "词库统计",
                    subtitle = "${ui.bookCount} 个词库 · ${ui.wordCount} 个单词",
                    trailing = {}
                )

                SettingRow(
                    title = "清除缓存",
                    subtitle = "上次清理：${ui.lastCacheClear}",
                    trailing = {
                        TextButton(onClick = { viewModel.clearCache() }) { Text("清理") }
                    }
                )

                SettingRow(
                    title = "清空学习记录",
                    subtitle = "删除进度与统计，保留词库",
                    titleColor = MaterialTheme.colorScheme.error,
                    trailing = {
                        TextButton(onClick = { showClearDataDialog = true }) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "单词速记 v1.0.0 · 数据全部存储在本地",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ============================================================
// 复用组件
// ============================================================

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** 数字步进器（新词数 / 复习数） */
@Composable
private fun NumberStepper(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = { onValueChange((value - step).coerceIn(range.first, range.last)) },
            enabled = value > range.first
        ) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        TextButton(
            onClick = { onValueChange((value + step).coerceIn(range.first, range.last)) },
            enabled = value < range.last
        ) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}
