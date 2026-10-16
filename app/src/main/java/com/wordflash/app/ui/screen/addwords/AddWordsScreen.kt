package com.wordflash.app.ui.screen.addwords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController

/**
 * 向已有词库添加单词。
 *
 * 进来的方式：词库列表页每个词库右侧的「+」按钮。
 *
 * 与新建词库页相比只有两点不同：词库名是只读的，
 * 保存走追加逻辑而不是新建词库。录入交互完全一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWordsScreen(
    navController: NavHostController,
    viewModel: AddWordsViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 保存成功后返回上一页，让用户看到词库列表里数字变了
    LaunchedEffect(ui.saved) {
        if (ui.saved) {
            ui.message?.let { snackbarHostState.showSnackbar(it) }
            viewModel.consumedSavedFlag()
            navController.popBackStack()
        }
    }

    LaunchedEffect(ui.message) {
        if (!ui.saved) {
            ui.message?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearMessage()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加单词") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ==================== 目标词库（只读） ====================
            item {
                Spacer(Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "添加到",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = ui.bookName.ifBlank { "（读取中…）" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (ui.existingCount > 0) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "这个词库目前有 ${ui.existingCount} 个单词",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // ==================== 方式一：逐个添加 ====================
            item {
                SectionCard(
                    title = "① 逐个添加",
                    subtitle = "单词和释义填好，点下面的按钮加入列表"
                ) {
                    OutlinedTextField(
                        value = ui.inputSpelling,
                        onValueChange = { viewModel.setInputSpelling(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("单词 *") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ui.inputMeaning,
                        onValueChange = { viewModel.setInputMeaning(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("释义 *") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ui.inputPhonetic,
                        onValueChange = { viewModel.setInputPhonetic(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("音标（可选）") },
                        placeholder = { Text("/ˈæpl/") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.addSingleWord() },
                        enabled = ui.canAddSingle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("加入列表")
                    }
                }
            }

            // ==================== 方式二：批量粘贴 ====================
            item {
                SectionCard(
                    title = "② 批量粘贴",
                    subtitle = "一次贴一整批，每行一个单词"
                ) {
                    OutlinedTextField(
                        value = ui.pasteText,
                        onValueChange = { viewModel.setPasteText(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        label = { Text("粘贴文本") },
                        placeholder = {
                            Text("apple 苹果\nbanana,香蕉\norange\t橙子")
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.parsePasted() },
                        enabled = ui.pasteText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("解析并加入列表")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "用空格、逗号或制表符隔开单词和释义都行；" +
                            "也能直接从 Excel 复制一列粘进来",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ==================== 草稿列表 ====================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "待添加：${ui.draftWords.size} 个单词",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (ui.draftWords.isNotEmpty()) {
                        OutlinedButton(onClick = { viewModel.clearDrafts() }) {
                            Text("清空")
                        }
                    }
                }
            }

            itemsIndexed(ui.draftWords) { index, word ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = word.spelling,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (word.phonetic.isNotBlank()) {
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        text = word.phonetic,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = word.meaning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.removeDraft(index) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "移除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ==================== 保存 ====================
            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { viewModel.save() },
                    enabled = ui.canSave && !ui.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        if (ui.isSaving) "保存中…"
                        else "添加到「${ui.bookName}」（${ui.draftWords.size} 词）"
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
