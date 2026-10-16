package com.wordflash.app.ui.screen.wordbook

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.wordflash.app.data.repository.StudyRepository
import com.wordflash.app.ui.components.EmptyState
import com.wordflash.app.ui.navigation.Routes
import com.wordflash.app.ui.theme.IncorrectRed

/**
 * 「我的单词」页：错题本 / 生词本 / 收藏。
 *
 * 三个分页共用同一套布局，只是数据源不同。
 * 错题本额外支持：错因、自定义标签、薄弱/掌握标记、按标签与错因筛选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordbookScreen(
    navController: NavHostController,
    viewModel: WordbookViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var tagEditorWordId by remember { mutableStateOf<Long?>(null) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空错题本？") },
            text = { Text("会移除错题本里的全部单词。学习进度、错因和标签都会保留。") },
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

    // 标签编辑弹窗
    tagEditorWordId?.let { wordId ->
        TagEditorDialog(
            initial = ui.wrongTags[wordId] ?: emptyList(),
            allTags = ui.allTags,
            onDismiss = { tagEditorWordId = null },
            onSave = {
                viewModel.saveTags(wordId, it)
                tagEditorWordId = null
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

            // ---------- 错题本专属操作区 ----------
            if (ui.tab == WordbookViewModel.Tab.WRONG) {
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
                        enabled = ui.wrongCount > 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("练习错题")
                    }
                    // 手动往错题本里补词 —— 有些词是做错的题没覆盖到的
                    OutlinedButton(
                        onClick = {
                            // 复用全局搜索页：选词后加入错题本
                            navController.navigate(Routes.SEARCH_TO_WRONG_BOOK)
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("加词")
                    }
                    if (ui.wrongCount > 0) {
                        OutlinedButton(onClick = { showClearDialog = true }) {
                            Text("清空")
                        }
                    }
                }
                Text(
                    text = "连续答对 2 次会自动移出错题本；也可以手动标记掌握",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // 快捷筛选
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WordbookViewModel.WrongFilter.entries.forEach { f ->
                        FilterChip(
                            selected = ui.filter == f,
                            onClick = { viewModel.setFilter(f) },
                            label = { Text(f.label) }
                        )
                    }
                }
            }

            // ---------- 搜索 ----------
            OutlinedTextField(
                value = ui.query,
                onValueChange = { viewModel.setQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = {
                    Text(
                        if (ui.tab == WordbookViewModel.Tab.WRONG) {
                            "搜索单词、释义、错因或标签"
                        } else {
                            "搜索单词或释义"
                        }
                    )
                },
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
                            wrongReason = ui.wrongReasons[word.id].orEmpty(),
                            tags = ui.wrongTags[word.id].orEmpty(),
                            isEditingReason = ui.editingReasonWordId == word.id,
                            onSpeak = { viewModel.speak(word.spelling) },
                            onToggleFavorite = { viewModel.toggleFavorite(word.id) },
                            onStartEditReason = { viewModel.startEditingReason(word.id) },
                            onCancelEditReason = { viewModel.stopEditingReason() },
                            onSaveReason = { viewModel.saveReason(word.id, it) },
                            onEditTags = { tagEditorWordId = word.id },
                            onMarkWeak = {
                                if (ui.wrongTags[word.id].orEmpty().any {
                                        it.equals(StudyRepository.TAG_WEAK, ignoreCase = true)
                                    }
                                ) {
                                    viewModel.unmarkWeak(word.id)
                                } else {
                                    viewModel.markWeak(word.id)
                                }
                            },
                            onMarkMastered = { viewModel.markMastered(word.id) },
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
    ui.tab == WordbookViewModel.Tab.WRONG &&
        ui.filter != WordbookViewModel.WrongFilter.ALL -> "这个筛选下没有单词"
    ui.tab == WordbookViewModel.Tab.WRONG -> "错题本是空的"
    ui.tab == WordbookViewModel.Tab.WORDBOOK -> "生词本是空的"
    else -> "还没有收藏的单词"
}

private fun emptyDesc(ui: WordbookViewModel.UiState): String = when {
    ui.query.isNotBlank() -> "换个关键词，或者试试按标签搜"
    ui.tab == WordbookViewModel.Tab.WRONG &&
        ui.filter != WordbookViewModel.WrongFilter.ALL -> "换个筛选条件看看"
    ui.tab == WordbookViewModel.Tab.WRONG -> "答题出错的单词会自动收进这里，也可以手动添加"
    ui.tab == WordbookViewModel.Tab.WORDBOOK -> "在单词列表里点「加入生词本」手动收录"
    else -> "在学习时点星星即可收藏"
}

@Composable
private fun WordRow(
    word: Word,
    tab: WordbookViewModel.Tab,
    wrongCount: Int,
    wrongReason: String,
    tags: List<String>,
    isEditingReason: Boolean,
    onSpeak: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStartEditReason: () -> Unit,
    onCancelEditReason: () -> Unit,
    onSaveReason: (String) -> Unit,
    onEditTags: () -> Unit,
    onMarkWeak: () -> Unit,
    onMarkMastered: () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val isFavoriteTab = tab == WordbookViewModel.Tab.FAVORITE
    val isWrongTab = tab == WordbookViewModel.Tab.WRONG
    val isWeak = tags.any { it.equals(StudyRepository.TAG_WEAK, ignoreCase = true) }

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
                        if (isWrongTab && wrongCount > 0) {
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

                    // 标签行
                    if (isWrongTab && tags.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tags.forEach { tag ->
                                AssistChip(
                                    onClick = onEditTags,
                                    label = { Text(tag) },
                                    leadingIcon = if (tag.equals(
                                            StudyRepository.TAG_WEAK,
                                            ignoreCase = true
                                        )
                                    ) {
                                        {
                                            Icon(
                                                Icons.Filled.Warning,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }

                    // 错因
                    if (isWrongTab && wrongReason.isNotBlank() && !isEditingReason) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "错因：$wrongReason",
                            style = MaterialTheme.typography.bodySmall,
                            color = IncorrectRed
                        )
                    }
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

            // ---------- 展开区 ----------
            if (expanded) {
                if (word.example.isNotBlank()) {
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

                // 错题本专属：错因编辑 + 薄弱/掌握/标签
                if (isWrongTab) {
                    Spacer(Modifier.height(10.dp))

                    if (isEditingReason) {
                        ReasonEditor(
                            initial = wrongReason,
                            onCancel = onCancelEditReason,
                            onSave = onSaveReason
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = onStartEditReason) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(if (wrongReason.isBlank()) "记错因" else "改错因")
                            }
                            OutlinedButton(onClick = onEditTags) {
                                Text(if (tags.isEmpty()) "加标签" else "改标签")
                            }
                            OutlinedButton(onClick = onMarkWeak) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.size(4.dp))
                                Text(if (isWeak) "取消薄弱" else "标为薄弱")
                            }
                            OutlinedButton(onClick = onMarkMastered) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.size(4.dp))
                                Text("已掌握")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 错因内联编辑器 */
@Composable
private fun ReasonEditor(
    initial: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("错因") },
            placeholder = { Text("例如：和 adapt 混了 / 拼写总漏一个字母") },
            minLines = 2
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(text) }) { Text("保存") }
            OutlinedButton(onClick = onCancel) { Text("取消") }
        }
    }
}

/** 标签编辑弹窗：可删已有标签、加新标签、从历史标签里点选 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagEditorDialog(
    initial: List<String>,
    allTags: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var tags by remember { mutableStateOf(initial) }
    var input by remember { mutableStateOf("") }

    fun addTag(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        if (tags.any { it.equals(t, ignoreCase = true) }) return
        tags = tags + t
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标签") },
        text = {
            Column {
                // 已有标签
                if (tags.isNotEmpty()) {
                    Text(
                        text = "当前标签（点一下删除）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tags.forEach { tag ->
                            AssistChip(
                                onClick = { tags = tags.filterNot { it == tag } },
                                label = { Text(tag) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("新标签") },
                    placeholder = { Text("例如：形近词") },
                    trailingIcon = {
                        IconButton(onClick = {
                            addTag(input)
                            input = ""
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "添加")
                        }
                    }
                )

                // 历史标签快速点选
                val suggestions = allTags.filter { s ->
                    tags.none { it.equals(s, ignoreCase = true) }
                }
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "用过的标签",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestions.forEach { s ->
                            AssistChip(
                                onClick = { addTag(s) },
                                label = { Text(s) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 顺手把输入框里没提交的内容也带上，避免用户白打
                val merged = if (input.isBlank()) tags else {
                    (tags + input.trim()).distinct()
                }
                onSave(merged)
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
