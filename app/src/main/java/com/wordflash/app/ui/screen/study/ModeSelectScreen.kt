package com.wordflash.app.ui.screen.study

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wordflash.app.domain.model.StudyMode
import com.wordflash.app.ui.navigation.Routes

/**
 * 选择学习模式。
 *
 * 四种模式并列展示，各自说明用途，让用户按当下状态选择 ——
 * 「想快速过一遍」选卡片，「想确认记住了」选拼写。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectScreen(
    bookId: Long,
    navController: NavHostController,
    viewModel: ModeSelectViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val dueCounts by viewModel.dueCounts.collectAsStateWithLifecycle()

    val book = books.firstOrNull { it.id == bookId }
    val dueCount = dueCounts[bookId] ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.name ?: "选择模式") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            val total = book?.wordCount ?: 0
            Text(
                text = buildString {
                    append("共 $total 词")
                    if (dueCount > 0) append(" · 待复习 $dueCount")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            StudyMode.entries.forEach { mode ->
                ModeRow(mode = mode) {
                    navController.navigate(Routes.study(bookId, mode.key))
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModeRow(mode: StudyMode, onClick: () -> Unit) {
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
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
