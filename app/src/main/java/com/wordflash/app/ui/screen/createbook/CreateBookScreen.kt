package com.wordflash.app.ui.screen.createbook

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
 * 新建自定义词库。
 *
 * 页面只做两件事：**起个名字**，然后**把单词录进去**。
 *
 * 两种录入方式可以混用：先手打几个，再粘贴一批。
 * 结果都汇入下方同一个草稿列表，最后一次性保存。
 *
 * 之所以不做「从文件导入」：词库通常只有几十到几百词，
 * 粘贴一次就够；文件选择器要多处理权限、编码、格式嗅探，
 * 对用户来说反而多了一道坎。需要批量导入时，从 Excel 复制整列粘贴即可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBookScreen(
    navController: NavHostController,
    viewModel: CreateBookViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 保存成功后返回上一页
    LaunchedEffect(ui.saved) {
        if (ui.saved) {
            ui.message?.let { snackbarHostState.showSnackbar(it) }
            viewModel.consumedSavedFlag()
            navController.popBackStack()
        }
    }

    LaunchedEffect(ui.message) {
        // saved 分支已经弹过一次，避免重复
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
                title = { Text("新建词库") },
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
            // ==================== 词库名 ====================
            item {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = ui.bookName,
                    onValueChange = { viewModel.setBookName(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("词库名称 *") },
                    isError = ui.nameError != null,
                    supportingText = ui.nameError?.let { { Text(it) } }
                )
            }

            // ==================== 方式一：逐条添加 ====================
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
                        placeholder = { Text("/əˈbændən/") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.addSingleWord() },
                        enabled = ui.canAddSingle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("添加到列表")
                    }
                }
            }

            // ==================== 方式二：粘贴批量 ====================
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
                            Text("abandon 放弃\nabsorb,吸收\nabstract\t抽象的")
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
                        text = "待保存：${ui.draftWords.size} 个单词",
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
                                Icons.Filled.Close,
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
                        else "保存词库（${ui.draftWords.size} 词）"
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
