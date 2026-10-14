package com.wordflash.app.ui.screen.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wordflash.app.ui.components.AccuracyRing
import com.wordflash.app.ui.components.StatChip
import com.wordflash.app.ui.theme.CorrectGreen
import com.wordflash.app.util.DateUtils

/**
 * 学习结果页。
 *
 * 数据全部通过导航参数传入（不走 ViewModel），
 * 因为这是一次性展示，不需要响应式更新，也不需要回数据库查。
 */
@Composable
fun ResultScreen(
    total: Int,
    correct: Int,
    durationMs: Long,
    onBackHome: () -> Unit,
    onStudyAgain: () -> Unit,
    onViewWordbook: () -> Unit
) {
    val accuracy = if (total == 0) 0f else correct.toFloat() / total
    val wrong = total - correct

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "本轮学习完成",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(28.dp))

        AccuracyRing(
            fraction = accuracy,
            centerText = "${(accuracy * 100).toInt()}%",
            ringColor = if (accuracy >= 0.8f) CorrectGreen else MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatChip(value = "$total", label = "总题数")
                StatChip(value = "$correct", label = "答对")
                StatChip(value = "$wrong", label = "答错")
                StatChip(value = DateUtils.formatDuration(durationMs), label = "用时")
            }
        }

        if (wrong > 0) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "答错的单词已自动加入生词本",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = onBackHome,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("返回首页")
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onStudyAgain,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("再学一轮")
            }
            OutlinedButton(
                onClick = onViewWordbook,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Text("看生词本")
            }
        }
    }
}
