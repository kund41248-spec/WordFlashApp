package com.wordflash.app.ui.screen.createbook

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController

/**
 * 新建自定义词库。
 *
 * 三种录入方式并列，用户可以混用：
 * 手打几个 + 再粘贴一批 + 从文件导入剩下的。
 * 全部汇入下方同一个草稿列表，最后一次性保存。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBookScreen(
    navController: NavHostController,
    viewModel: CreateBookViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importFromFile(context, uri)
    }

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
                    title = "① 逐条添加",
                    subtitle = "拼写与释义必填，音标选填"
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
                    title = "② 粘贴批量导入",
                    subtitle = "每行一个词，如：abandon 放弃"
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
                        text = "支持空格、逗号、制表符分隔；也能从 Excel 直接复制整列",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ==================== 方式三：文件导入 ====================
            item {
                SectionCard(
                    title = "③ 从文件导入",
                    subtitle = "CSV / TXT / JSON，结果同样进入下方列表"
                ) {
                    OutlinedButton(
                        onClick = {
                            filePicker.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/json",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("选择文件")
                    }
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
