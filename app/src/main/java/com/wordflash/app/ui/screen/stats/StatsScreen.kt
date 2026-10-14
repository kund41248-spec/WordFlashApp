package com.wordflash.app.ui.screen.stats

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wordflash.app.ui.components.AccuracyRing
import com.wordflash.app.ui.components.StatChip
import com.wordflash.app.ui.theme.CorrectGreen
import com.wordflash.app.ui.theme.IncorrectRed
import com.wordflash.app.util.DateUtils

/**
 * 统计页。
 *
 * 图表全部用 Canvas 自绘，不引入第三方图表库 ——
 * MPAndroidChart 等库体积在 1MB 以上，而这里只需要折线图与柱状图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavHostController,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("学习统计") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- 总览 ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AccuracyRing(
                            fraction = ui.accuracy,
                            centerText = "${(ui.accuracy * 100).toInt()}%",
                            ringColor = if (ui.accuracy >= 0.8f) {
                                CorrectGreen
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "总正确率",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatChip(value = "${ui.totalReviews}", label = "累计词次")
                            StatChip(value = "${ui.streak}", label = "连续打卡")
                            StatChip(value = "${ui.studyDays}", label = "学习天数")
                            StatChip(
                                value = DateUtils.formatDuration(ui.totalDurationMs),
                                label = "总时长"
                            )
                        }
                    }
                }
            }

            // ---------- 时间范围 ----------
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsViewModel.Range.entries.forEach { range ->
                        FilterChip(
                            selected = ui.range == range,
                            onClick = { viewModel.setRange(range) },
                            label = { Text(range.label) }
                        )
                    }
                }
            }

            // ---------- 每日学习量 ----------
            item {
                ChartCard(title = "每日学习量") {
                    if (ui.dailyList.isEmpty()) {
                        ChartEmpty("这段时间还没有学习记录")
                    } else {
                        DailyBarChart(
                            values = ui.dailyList.map { it.reviewCount },
                            labels = ui.dailyList.map { DateUtils.toDisplayDate(it.date) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                        )
                    }
                }
            }

            // ---------- 正确率趋势 ----------
            item {
                ChartCard(title = "正确率趋势") {
                    if (ui.dailyList.isEmpty()) {
                        ChartEmpty("这段时间还没有学习记录")
                    } else {
                        AccuracyLineChart(
                            values = ui.dailyList.map { it.accuracy },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        )
                    }
                }
            }

            // ---------- 未来复习计划 ----------
            item {
                ChartCard(title = "未来 7 天复习量") {
                    if (ui.upcomingLoad.isEmpty()) {
                        ChartEmpty("暂无待复习安排")
                    } else {
                        UpcomingList(ui.upcomingLoad)
                    }
                }
            }

            // ---------- 每日明细 ----------
            if (ui.dailyList.isNotEmpty()) {
                item {
                    Text(
                        text = "每日明细",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }
                items(ui.dailyList.reversed()) { day ->
                    DayRow(
                        date = day.date,
                        learned = day.learnedCount,
                        reviews = day.reviewCount,
                        accuracy = day.accuracy,
                        durationMs = day.durationMs
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ChartEmpty(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 每日学习量柱状图。
 *
 * 柱高按最大值归一化；只有一天数据时也能正常显示。
 */
@Composable
private fun DailyBarChart(
    values: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val maxValue = values.maxOrNull()?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (values.isEmpty()) return@Canvas

            val slotWidth = size.width / values.size
            val barWidth = (slotWidth * 0.55f).coerceAtMost(28.dp.toPx())
            val gap = (slotWidth - barWidth) / 2f

            values.forEachIndexed { index, value ->
                val ratio = value.toFloat() / maxValue
                // 最小 2dp，保证 0 值也有一条可见基线
                val barHeight = (size.height * ratio).coerceAtLeast(2.dp.toPx())
                drawRoundRect(
                    color = if (value == 0) barColor.copy(alpha = 0.2f) else barColor,
                    topLeft = Offset(index * slotWidth + gap, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f)
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = labels.firstOrNull() ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "峰值 $maxValue",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = labels.lastOrNull() ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 正确率折线图。只有 1 个数据点时画圆点，画不出线。
 */
@Composable
private fun AccuracyLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.12f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas

        // 横向网格：0% / 50% / 100%
        listOf(0f, 0.5f, 1f).forEach { ratio ->
            val y = size.height * (1f - ratio)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        val stepX = if (values.size <= 1) 0f else size.width / (values.size - 1)
        val points = values.mapIndexed { index, v ->
            Offset(
                x = if (values.size <= 1) size.width / 2f else index * stepX,
                y = size.height * (1f - v.coerceIn(0f, 1f))
            )
        }

        if (points.size == 1) {
            drawCircle(color = lineColor, radius = 5.dp.toPx(), center = points.first())
            return@Canvas
        }

        val fillPath = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(fillPath, color = fillColor)

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(linePath, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))
    }
}

/** 未来 7 天复习量 */
@Composable
private fun UpcomingList(load: List<Pair<Long, Int>>) {
    val maxCount = load.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1

    Column {
        // 颜色必须在 Composable 上下文里读出来。
        // Canvas 的绘制 lambda 是 DrawScope.() -> Unit，不是 @Composable，
        // 在里面读 MaterialTheme 会编译报错。
        val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)

        load.take(7).forEach { (dayStart, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateUtils.toDisplayDate(DateUtils.formatDate(dayStart)),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(64.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                    ) {
                        val w = size.width * (count.toFloat() / maxCount)
                        drawRoundRect(
                            color = barColor,
                            size = Size(w.coerceAtLeast(2f), size.height),
                            cornerRadius = CornerRadius(7f, 7f)
                        )
                    }
                }
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/** 单日明细行 */
@Composable
private fun DayRow(
    date: String,
    learned: Int,
    reviews: Int,
    accuracy: Float,
    durationMs: Long
) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DateUtils.toDisplayDate(date),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "新学 $learned · 复习 $reviews · ${DateUtils.formatDuration(durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${(accuracy * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    accuracy >= 0.8f -> CorrectGreen
                    accuracy >= 0.5f -> MaterialTheme.colorScheme.primary
                    else -> IncorrectRed
                }
            )
        }
    }
}
