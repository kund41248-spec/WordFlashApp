package com.wordflash.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 字体规范。
 *
 * 中文正文行高比英文需要更宽松（1.5 倍左右），否则密集文字会显得拥挤，
 * 因此统一把 lineHeight 抬高，并用 LineHeightStyle 让行距均匀分布。
 */
private val cjkLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun cjk(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Double = 0.0
): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = cjkLineHeightStyle
)

val AppTypography = Typography(
    // 大标题：用于页面主标题
    headlineLarge = cjk(28, 38, FontWeight.Bold),
    headlineMedium = cjk(24, 34, FontWeight.Bold),
    headlineSmall = cjk(20, 30, FontWeight.SemiBold),

    // 小标题：卡片标题、列表分组
    titleLarge = cjk(20, 28, FontWeight.SemiBold),
    titleMedium = cjk(17, 26, FontWeight.Medium),
    titleSmall = cjk(15, 22, FontWeight.Medium),

    // 正文
    bodyLarge = cjk(16, 26),
    bodyMedium = cjk(14, 23),
    bodySmall = cjk(13, 20),

    // 按钮、标签
    labelLarge = cjk(15, 22, FontWeight.Medium),
    labelMedium = cjk(13, 18, FontWeight.Medium),
    labelSmall = cjk(11, 16, FontWeight.Medium)
)

/**
 * 单词展示专用样式：单词需要比正文显著更大更粗，且字距略宽，
 * 便于在卡片上快速识别。英文用默认字体即可保证各机型字形一致。
 */
object WordTextStyles {
    val spelling = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.5.sp
    )
    val phonetic = TextStyle(
        fontFamily = FontFamily.Serif,   // 音标用衬线体，接近词典排版
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
    val meaning = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 32.sp
    )
    val example = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 25.sp
    )
}
