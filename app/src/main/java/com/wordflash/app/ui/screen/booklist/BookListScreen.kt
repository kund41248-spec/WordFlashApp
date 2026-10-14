package com.wordflash.app.ui.screen.booklist

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.wordflash.app.data.local.entity.BookCategory
import com.wordflash.app.data.local.entity.WordBook
import com.wordflash.app.ui.components.EmptyState
import com.wordflash.app.ui.navigation.Routes
import com.wordflash.app.ui.screen.study.ModeSelectViewModel

/**
 * 词库列表页。点击某个词库进入模式选择。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    navController: NavHostController,
    viewModel: ModeSelectViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val dueCounts by viewModel.dueCounts.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("词库") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        if (books.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "还没有词库",
                    description = "内置词库正在导入，请稍候片刻"
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ImportButton { navController.navigate(Routes.CREATE_BOOK) }
            }
            items(books, key = { it.id }) { book ->
                BookRow(
                    book = book,
                    dueCount = dueCounts[book.id] ?: 0,
                    onClick = { navController.navigate(Routes.modeSelect(book.id)) },
                    onDelete = { viewModel.deleteBook(book) }
                )
            }
        }
    }
}

@Composable
private fun ImportButton(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "新建自定义词库",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "逐条输入 / 粘贴文本 / 从文件导入",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun BookRow(
    book: WordBook,
    dueCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = book.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (book.isBuiltIn) {
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = "内置",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append("${book.wordCount} 词")
                        append(" · ")
                        append(categoryLabel(book.category))
                        if (dueCount > 0) append(" · 待复习 $dueCount")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dueCount > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (!book.isBuiltIn) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除词库",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun categoryLabel(category: String): String = when (category) {
    BookCategory.CET4 -> "四级"
    BookCategory.CET6 -> "六级"
    BookCategory.KAOYAN -> "考研"
    BookCategory.GAOKAO -> "高考"
    BookCategory.IELTS -> "雅思"
    BookCategory.TOEFL -> "托福"
    BookCategory.CUSTOM -> "自定义"
    else -> category
}
