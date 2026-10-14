package com.wordflash.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 学习进度条 + 题号。
 *
 * 放在学习页顶部，让用户随时知道还剩多少。
 */
@Composable
fun StudyProgressBar(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val target = if (total <= 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = target, label = "progress")

    Column(modifier = modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MaterialTheme.shapes.small),
            strokeCap = StrokeCap.Round
        )
        Text(
            text = "$current / $total",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * 空状态占位。
 *
 * 用于「今日没有需要复习的单词」「生词本是空的」等场景。
 * 包含标题 + 说明 + 可选操作按钮。
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = 20.dp)) { action() }
        }
    }
}

/**
 * 数值 + 标签的统计小卡片。
 */
@Composable
fun StatChip(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 正确率环形图（自绘，不引入第三方图表库）。
 *
 * @param fraction 0f~1f
 */
@Composable
fun AccuracyRing(
    fraction: Float,
    centerText: String,
    modifier: Modifier = Modifier,
    ringColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        label = "accuracy"
    )

    Box(
        modifier = modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(140.dp)) {
            val stroke = 14.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)

            // 底环
            drawArc(
                color = ringColor.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = StrokeCap.Round
                )
            )
            // 进度环：从 12 点方向顺时针
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = StrokeCap.Round
                )
            )
        }
        Text(
            text = centerText,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
