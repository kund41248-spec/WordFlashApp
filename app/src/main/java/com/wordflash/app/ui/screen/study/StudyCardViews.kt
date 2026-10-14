package com.wordflash.app.ui.screen.study

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wordflash.app.domain.model.StudyCard
import com.wordflash.app.ui.theme.WordTextStyles

/**
 * 单词卡片（正面英文 / 背面释义）。
 *
 * 用 `graphicsLayer` 做 Y 轴翻转动画：`rotationY` 超过 90° 时
 * 换显示内容，视觉上就是"翻面"。
 */
@Composable
fun FlashCardFace(
    card: StudyCard,
    isFlipped: Boolean,
    ttsAvailable: Boolean,
    onSpeak: () -> Unit,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "flip"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onFlip() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                },
            contentAlignment = Alignment.Center
        ) {
            // 背面在 90° 之后显示，需要再翻 180° 否则文字是镜像的
            val showingBack = rotation > 90f
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .graphicsLayer { if (showingBack) rotationY = 180f }
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (!showingBack) {
                    FrontContent(card, ttsAvailable, onSpeak)
                } else {
                    BackContent(card)
                }
            }
        }
    }
}

@Composable
private fun FrontContent(
    card: StudyCard,
    ttsAvailable: Boolean,
    onSpeak: () -> Unit
) {
    Text(
        text = card.spelling,
        style = WordTextStyles.spelling,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )
    if (card.phonetic.isNotBlank()) {
        Text(
            text = card.phonetic,
            style = WordTextStyles.phonetic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
    if (ttsAvailable) {
        IconButton(
            onClick = onSpeak,
            modifier = Modifier
                .padding(top = 16.dp)
                .size(52.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.VolumeUp,
                contentDescription = "播放发音",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
    }
    Text(
        text = "点击卡片查看释义",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp)
    )
}

@Composable
private fun BackContent(card: StudyCard) {
    if (card.partOfSpeech.isNotBlank()) {
        Text(
            text = card.partOfSpeech,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Text(
        text = card.meaning,
        style = WordTextStyles.meaning,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 6.dp)
    )
    if (card.example.isNotBlank()) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = card.example,
            style = WordTextStyles.example,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (card.exampleTranslation.isNotBlank()) {
            Text(
                text = card.exampleTranslation,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * 选项按钮（选择题 / 听音辨词共用）。
 *
 * 三种视觉状态：未作答（默认）、我选的且正确（绿）、我选的且错误（红）。
 * 正确答案在答错时也会标绿，让用户知道正确选项是哪个。
 */
@Composable
fun OptionButton(
    text: String,
    isSelected: Boolean,
    isCorrectAnswer: Boolean,
    answered: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val container = when {
        !answered -> colors.surface
        isCorrectAnswer -> com.wordflash.app.ui.theme.CorrectGreen.copy(alpha = 0.18f)
        isSelected -> com.wordflash.app.ui.theme.IncorrectRed.copy(alpha = 0.18f)
        else -> colors.surface
    }
    val border = when {
        !answered -> colors.outline
        isCorrectAnswer -> com.wordflash.app.ui.theme.CorrectGreen
        isSelected -> com.wordflash.app.ui.theme.IncorrectRed
        else -> colors.outline
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(enabled = !answered, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, border)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (answered && isCorrectAnswer) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        )
    }
}
