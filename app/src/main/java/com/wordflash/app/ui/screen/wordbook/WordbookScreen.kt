package com.wordflash.app.ui.screen.wordbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wordflash.app.data.local.entity.Word
import com.wordflash.app.ui.components.EmptyState
import com.wordflash.app.ui.navigation.Routes
import com.wordflash.app.ui.theme.IncorrectRed

/**
 * 「我的单词」页：错题本 / 生词本 / 收藏。
 *
 * 三个分页共用同一套布局，只是数据源不同。
 * 错题本额外显示错误次数，并提供「专项练习」入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordbookScreen(
    navController: NavHostController,
    viewModel: WordbookViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空错题本？") },
            text = { Text("会移除错题本里的全部单词。学习进度与统计数据不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    viewModel.clearWrongBook()
                }) {
                    Text("确认清空", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("我的单词") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ---------- 分页 ----------
            TabRow(selectedTabIndex = ui.tab.ordinal) {
                WordbookViewModel.Tab.entries.forEach { tab ->
                    val count = when (tab) {
                        WordbookViewModel.Tab.WRONG -> ui.wrongCount
                        WordbookViewModel.Tab.WORDBOOK -> ui.wordbookCount
                        WordbookViewModel.Tab.FAVORITE -> ui.favoriteCount
                    }
                    Tab(
                        selected = ui.tab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = { Text("${tab.label} $count") }
                    )
                }
            }

            // ---------- 错题本专属：专项练习 ----------
            if (ui.tab == WordbookViewModel.Tab.WRONG && ui.wrongCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            // 错题列表由 StudyViewModel 自己取 ——
                            // 走导航参数传一长串 id 既冗长又容易出错
                            navController.navigate(Routes.practiceWrong())
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("练习错题")
                    }
                    OutlinedButton(onClick = { showClearDialog = true }) {
                        Text("清空")
                    }
                }
                Text(
                    text = "答对的单词会自动移出错题本",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // ---------- 搜索 ----------
            OutlinedTextField(
                value = ui.query,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("搜索单词或释义") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }
            )

            // ---------- 列表 ----------
            if (ui.words.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = emptyTitle(ui),
                        description = emptyDesc(ui)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ui.words, key = { it.id }) { word ->
                        WordRow(
                            word = word,
                            tab = ui.tab,
                            wrongCount = ui.wrongCounts[word.id] ?: 0,
                            onSpeak = { viewModel.speak(word.spelling) },
                            onToggleFavorite = { viewModel.toggleFavorite(word.id) },
                            onRemove = {
                                when (ui.tab) {
                                    WordbookViewModel.Tab.WRONG ->
                                        viewModel.removeFromWrongBook(word.id)
                                    WordbookViewModel.Tab.WORDBOOK ->
                                        viewModel.toggleWordbook(word.id)
                                    WordbookViewModel.Tab.FAVORITE ->
                                        viewModel.toggleFavorite(word.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun emptyTitle(ui: WordbookViewModel.UiState): String = when {
    ui.query.isNotBlank() -> "没有找到匹配的单词"
    ui.tab == WordbookViewModel.Tab.WRONG -> "错题本是空的"
    ui.tab == WordbookViewModel.Tab.WORDBOOK -> "生词本是空的"
    else -> "还没有收藏的单词"
}

private fun emptyDesc(ui: WordbookViewModel.UiState): String = when {
    ui.query.isNotBlank() -> "换个关键词试试"
    ui.tab == WordbookViewModel.Tab.WRONG -> "答题出错的单词会自动收进这里"
    ui.tab == WordbookViewModel.Tab.WORDBOOK -> "在单词列表里点「加入生词本」手动收录"
    else -> "在学习时点星星即可收藏"
}

@Composable
private fun WordRow(
    word: Word,
    tab: WordbookViewModel.Tab,
    wrongCount: Int,
    onSpeak: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFavoriteTab = tab == WordbookViewModel.Tab.FAVORITE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(
                start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = word.spelling,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (word.phonetic.isNotBlank()) {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = word.phonetic,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 错题本显示错误次数
                        if (tab == WordbookViewModel.Tab.WRONG && wrongCount > 0) {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "错 $wrongCount 次",
                                style = MaterialTheme.typography.labelSmall,
                                color = IncorrectRed
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = word.meaning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onSpeak) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = "发音",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavoriteTab) {
                            Icons.Filled.Star
                        } else {
                            Icons.Filled.StarBorder
                        },
                        contentDescription = if (isFavoriteTab) "取消收藏" else "收藏",
                        tint = if (isFavoriteTab) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "移出列表",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded && word.example.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = word.example,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (word.exampleTranslation.isNotBlank()) {
                    Text(
                        text = word.exampleTranslation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
