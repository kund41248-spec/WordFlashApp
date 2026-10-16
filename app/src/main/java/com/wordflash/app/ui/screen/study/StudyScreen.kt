package com.wordflash.app.ui.screen.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordflash.app.domain.model.StudyMode
import com.wordflash.app.tts.TtsManager
import com.wordflash.app.ui.components.EmptyState
import com.wordflash.app.ui.components.StudyProgressBar
import com.wordflash.app.ui.theme.CorrectGreen
import com.wordflash.app.ui.theme.IncorrectRed
import com.wordflash.app.ui.theme.WordTextStyles

/**
 * 学习页。
 *
 * 一个页面承载四种模式 —— 因为它们的骨架完全一致：
 * 「顶部进度 + 中间题目 + 底部操作」，只是中间区域渲染不同。
 * 拆成四个 Composable 会增加导航与状态传递的复杂度，收益不大。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    bookId: Long,
    modeKey: String,
    onBack: () -> Unit,
    onFinished: (total: Int, correct: Int, durationMs: Long) -> Unit,
    /**
     * 是否是「错题专项练习」。
     * 为 true 时不按词库取词，而是把错题本整批拿来练。
     */
    practiceWrong: Boolean = false,
    viewModel: StudyViewModel = hiltViewModel()
) {
    val state by viewModel.sessionState.collectAsStateWithLifecycle()
    val ttsStatus by viewModel.ttsStatus.collectAsStateWithLifecycle()
    val bookName by viewModel.bookName.collectAsStateWithLifecycle()
    val selected by viewModel.selectedOption.collectAsStateWithLifecycle()
    val selectedWordId by viewModel.selectedOptionWordId.collectAsStateWithLifecycle()
    val spellingFeedback by viewModel.spellingFeedback.collectAsStateWithLifecycle()

    val mode = remember(modeKey) { StudyMode.fromKey(modeKey) }
    val ttsAvailable = ttsStatus == TtsManager.Status.READY

    // 进入页面时启动会话
    LaunchedEffect(bookId, modeKey, practiceWrong) {
        if (practiceWrong) {
            viewModel.startWrongPractice(modeKey)
        } else {
            viewModel.start(bookId, modeKey)
        }
    }

    // 会话结束时跳转结果页
    LaunchedEffect(state.isFinished) {
        if (state.isFinished) {
            val s = state.summary
            if (s != null) {
                onFinished(s.totalQuestions, s.correctCount, s.durationMs)
            } else {
                // 没有可学的词，直接返回
                onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (bookName.isNotBlank()) {
                            Text(
                                text = bookName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.abandon()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->

        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))

            state.error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "加载失败",
                    description = state.error ?: "",
                    action = {
                        Button(onClick = { viewModel.start(bookId, modeKey) }) {
                            Text("重试")
                        }
                    }
                )
            }

            state.cards.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "暂时没有需要学习的单词",
                    description = "该词库的单词都已复习完成，明天再来吧",
                    action = {
                        OutlinedButton(onClick = onBack) { Text("返回") }
                    }
                )
            }

            else -> {
                val card = state.currentCard
                if (card == null) {
                    LoadingBox(Modifier.padding(padding))
                    return@Scaffold
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp)
                        .imePadding()
                ) {
                    StudyProgressBar(
                        current = state.currentIndex,
                        total = state.cards.size,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(Modifier.height(20.dp))

                    // 高亮只在「高亮所属的题目 == 当前题目」时才生效。
                    // 答题后 currentIndex 立刻前进，而高亮要再停留一会儿，
                    // 不加这个判断就会把上一题的高亮画到下一题上 ——
                    // 那等于直接把答案标出来了。
                    val activeSelection =
                        selected.takeIf { selectedWordId == card.wordId }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        when (mode) {
                            StudyMode.FLASHCARD -> FlashCardFace(
                                card = card,
                                isFlipped = state.isFlipped,
                                ttsAvailable = ttsAvailable,
                                onSpeak = { viewModel.speak(card.spelling) },
                                onFlip = { viewModel.flip() }
                            )

                            StudyMode.FILL_BLANK -> ChoiceQuestion(
                                prompt = "请选择正确的释义",
                                title = card.spelling,
                                subtitle = card.phonetic,
                                ttsAvailable = ttsAvailable,
                                onSpeak = { viewModel.speak(card.spelling) },
                                options = card.meaningOptions,
                                correctAnswer = card.meaning,
                                selected = activeSelection,
                                onSelect = { viewModel.selectOption(it, card.meaning) }
                            )

                            StudyMode.LISTENING -> ListeningQuestion(
                                card = card,
                                ttsAvailable = ttsAvailable,
                                selected = activeSelection,
                                onSpeak = { viewModel.speak(card.spelling) },
                                onSelect = { viewModel.selectOption(it, card.spelling) }
                            )

                            StudyMode.SPELLING -> SpellingQuestion(
                                card = card,
                                ttsAvailable = ttsAvailable,
                                feedback = spellingFeedback,
                                onSpeak = { viewModel.speak(card.spelling) },
                                onSubmit = { viewModel.submitSpelling(it, card.spelling) },
                                onNext = { viewModel.advanceFromSpelling() }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 卡片模式需要「认识 / 不认识」两个按钮；
                    // 其它模式在各自组件内完成作答，这里只留跳过
                    if (mode == StudyMode.FLASHCARD && state.isFlipped) {
                        AnswerButtons(
                            onKnown = { viewModel.markKnown() },
                            onUnknown = { viewModel.markUnknown() }
                        )
                    } else if (mode == StudyMode.FLASHCARD) {
                        Button(
                            onClick = { viewModel.flip() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text("查看释义")
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/** 认识 / 不认识 */
@Composable
private fun AnswerButtons(
    onKnown: () -> Unit,
    onUnknown: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onUnknown,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = IncorrectRed)
        ) {
            Text("不认识")
        }
        Button(
            onClick = onKnown,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CorrectGreen)
        ) {
            Text("认识")
        }
    }
}

/** 选词填空：看单词选释义 */
@Composable
private fun ChoiceQuestion(
    prompt: String,
    title: String,
    subtitle: String,
    ttsAvailable: Boolean,
    onSpeak: () -> Unit,
    options: List<String>,
    correctAnswer: String,
    selected: String?,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = WordTextStyles.spelling,
            textAlign = TextAlign.Center
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = WordTextStyles.phonetic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (ttsAvailable) {
            IconButton(onClick = onSpeak, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = "播放发音",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        options.forEach { option ->
            OptionButton(
                text = option,
                isSelected = selected == option,
                isCorrectAnswer = option == correctAnswer,
                answered = selected != null,
                onClick = { onSelect(option) }
            )
        }
    }
}

/**
 * 听音辨词：只听发音，从选项中选出对应单词。
 *
 * 与选词填空相反 —— 题干隐藏拼写，避免"看到答案选答案"。
 *
 * 发音只在这里、在题目真正显示时才播放。答题后的自动播放
 * （StudyViewModel.maybeAutoPlayNext）刻意跳过了本模式，
 * 否则下一题的答案会在用户还没看到题目时就被念出来。
 */
@Composable
private fun ListeningQuestion(
    card: com.wordflash.app.domain.model.StudyCard,
    ttsAvailable: Boolean,
    selected: String?,
    onSpeak: () -> Unit,
    onSelect: (String) -> Unit
) {
    // 进入题目自动播放一次，这是听音题的自然交互。
    // key 为 wordId：每换一题只播一次，重播靠用户点按钮。
    LaunchedEffect(card.wordId) {
        if (ttsAvailable) onSpeak()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "请听发音，选出对应的单词",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))

        if (ttsAvailable) {
            IconButton(onClick = onSpeak, modifier = Modifier.size(96.dp)) {
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = "重新播放",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }
            Text(
                text = "点击重播",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "当前设备不支持语音朗读",
                style = MaterialTheme.typography.bodyMedium,
                color = IncorrectRed
            )
        }

        Spacer(Modifier.height(28.dp))
        card.spellingOptions.forEach { option ->
            OptionButton(
                text = option,
                isSelected = selected == option,
                isCorrectAnswer = option == card.spelling,
                answered = selected != null,
                onClick = { onSelect(option) }
            )
        }
    }
}

/** 拼写默写：看释义、听发音，手打拼写 */
@Composable
private fun SpellingQuestion(
    card: com.wordflash.app.domain.model.StudyCard,
    ttsAvailable: Boolean,
    feedback: StudyViewModel.SpellingFeedback?,
    onSpeak: () -> Unit,
    onSubmit: (String) -> Unit,
    onNext: () -> Unit
) {
    var input by remember(card.wordId) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val answered = feedback != null
    val isCorrect = feedback?.isCorrect == true

    // 进入题目自动聚焦输入框并播放发音。
    // 答完（answered 变 true）后不再重复触发 —— key 换成 wordId+answered，
    // 但只在未作答时才播放与聚焦。
    LaunchedEffect(card.wordId) {
        if (ttsAvailable) onSpeak()
        focusRequester.requestFocus()
    }

    // 答错时停留久一点让用户看清，答对则快速进入下一题。
    // 用户也可以直接点「下一题」跳过等待。
    LaunchedEffect(feedback) {
        if (feedback != null) {
            keyboard?.hide()
            kotlinx.coroutines.delay(
                if (feedback.isCorrect) 700L else 2200L
            )
            onNext()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "请根据释义拼写单词",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

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
            textAlign = TextAlign.Center
        )

        if (ttsAvailable) {
            IconButton(onClick = onSpeak, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = "播放发音",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { if (!answered) input = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            enabled = !answered,
            label = { Text("输入单词") },
            isError = answered && !isCorrect,
            supportingText = if (answered && !isCorrect) {
                { Text("拼写不对", color = IncorrectRed) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(
                // 单词都是英文小写，关掉自动大写减少误输入
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboard?.hide()
                    if (input.isNotBlank() && !answered) onSubmit(input)
                }
            )
        )

        // ---------- 作答反馈 ----------
        if (feedback != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCorrect) {
                        CorrectGreen.copy(alpha = 0.14f)
                    } else {
                        IncorrectRed.copy(alpha = 0.14f)
                    }
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (isCorrect) CorrectGreen else IncorrectRed
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isCorrect) "拼对了" else "拼错了",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCorrect) CorrectGreen else IncorrectRed
                    )
                    Spacer(Modifier.height(8.dp))

                    if (!isCorrect) {
                        Text(
                            text = "你写的是：${feedback.input.ifBlank { "（空）" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = "正确答案：${feedback.expected}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (answered) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("下一题")
            }
        } else {
            Button(
                onClick = {
                    keyboard?.hide()
                    onSubmit(input)
                },
                enabled = input.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("提交")
            }

            Spacer(Modifier.height(12.dp))

            // 想不起来时可以直接跳过，避免卡住
            OutlinedButton(
                onClick = { onSubmit("") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("想不起来，跳过")
            }
        }
    }
}
