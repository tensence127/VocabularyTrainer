package com.example.cardapp.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardapp.data.Word
import com.example.cardapp.ui.AppViewModel
import com.example.cardapp.ui.CardMode
import com.example.cardapp.ui.ReviewSession
import com.example.cardapp.ui.components.CardModeSelector
import com.example.cardapp.ui.components.FlipCard
import com.example.cardapp.ui.components.LiquidGlass
import com.example.cardapp.ui.theme.AccentViolet
import com.example.cardapp.ui.plural
import kotlin.math.roundToInt

@Composable
fun ReviewScreen(
    vm: AppViewModel,
    onGoToWords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val words by vm.words.collectAsState()
    val session by vm.session.collectAsState()
    val cardMode by vm.cardMode.collectAsState()
    val highlight by vm.modeHighlight.collectAsState()
    val randomCount by vm.randomCount.collectAsState()
    val now = System.currentTimeMillis()
    val dueCount = words.count { it.nextReviewAt <= now }
    val sessionActive = session?.let { !it.finished } == true

    Column(modifier.fillMaxSize()) {
        CardModeSelector(
            selected = cardMode,
            highlight = highlight,
            onSelect = vm::selectCardMode,
            onElapsed = vm::updateModeHlElapsed,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            enabled = !sessionActive,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val s = session

            when {
                words.isEmpty() -> EmptyState(onGoToWords)
                s == null -> StartState(
                    dueCount = dueCount,
                    total = words.size,
                    randomCount = randomCount,
                    onRandomCountChange = vm::setRandomCount,
                    onStartDue = { vm.startSession(allWords = false) },
                    onStartRandom = { vm.startRandomSession() },
                )
                s.finished -> ResultState(
                    correct = s.correct,
                    answered = s.answered,
                    onDone = vm::closeSession,
                )
                else -> ActiveSession(
                    session = s,
                    mode = cardMode,
                    onAnswer = vm::answer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Показывать ли карточку «наоборот» (перевод спереди) в данном режиме. */
private fun isReversed(mode: CardMode, word: Word, answeredIndex: Int): Boolean = when (mode) {
    CardMode.MAIN -> false
    CardMode.REVERSE -> true
    CardMode.COMBO -> ((word.id.hashCode() * 31 + answeredIndex * 17) and 1) == 0
}

private data class CardFaces(val front: String, val back: String, val backLabel: String)

private fun facesFor(reversed: Boolean, word: Word): CardFaces {
    val translations = word.translations.joinToString("\n")
    return if (reversed) {
        CardFaces(front = translations, back = word.term, backLabel = "слово")
    } else {
        CardFaces(
            front = word.term,
            back = translations,
            backLabel = if (word.translations.size > 1) "переводы" else "перевод",
        )
    }
}

/** Карточка + счётчик ответа, чтобы AnimatedContent анимировал каждую смену. */
private data class CardFace(val word: Word, val index: Int)

@Composable
private fun ActiveSession(
    session: ReviewSession,
    mode: CardMode,
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val word = session.queue.first()
    val reversed = isReversed(mode, word, session.answered)
    val topPrompt = if (reversed) word.translations.joinToString(", ") else word.term

    Box(modifier) {
        Text(
            text = topPrompt,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
        )
        ActiveSessionContent(
            session = session,
            mode = mode,
            onAnswer = onAnswer,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun ActiveSessionContent(
    session: ReviewSession,
    mode: CardMode,
    onAnswer: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val word = session.queue.first()

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = "Осталось карточек: ${session.queue.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        AnimatedContent(
            targetState = CardFace(word, session.answered),
            transitionSpec = {
                (slideInHorizontally { it / 2 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 2 } + fadeOut())
            },
            label = "card",
        ) { face ->
            var flipped by remember { mutableStateOf(false) }
            val reversed = isReversed(mode, face.word, face.index)
            val faces = facesFor(reversed, face.word)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FlipCard(
                    frontText = faces.front,
                    backText = faces.back,
                    backLabel = faces.backLabel,
                    flipped = flipped,
                    onFlip = { flipped = true },
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .aspectRatio(1.5f),
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(targetState = flipped, label = "controls") { isFlipped ->
                        if (isFlipped) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AnswerButton(
                                    text = "Не помню",
                                    icon = Icons.Default.Close,
                                    color = MaterialTheme.colorScheme.error,
                                    onClick = { onAnswer(false) },
                                    modifier = Modifier.weight(1f),
                                )
                                AnswerButton(
                                    text = "Помню",
                                    icon = Icons.Default.Check,
                                    color = MaterialTheme.colorScheme.primary,
                                    onClick = { onAnswer(true) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            Text(
                                text = if (reversed) "Вспомни слово и нажми на карточку"
                                       else "Вспомни перевод и нажми на карточку",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartState(
    dueCount: Int,
    total: Int,
    randomCount: Int,
    onRandomCountChange: (Int) -> Unit,
    onStartDue: () -> Unit,
    onStartRandom: () -> Unit,
) {
    val effectiveRandom = minOf(randomCount, total)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dueCount > 0) {
            Text("К повторению", style = MaterialTheme.typography.titleMedium)
            Text("$dueCount", style = MaterialTheme.typography.displayMedium)
            Text(
                text = "из $total ${plural(total, "слова", "слов", "слов")} в словаре",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dueCount > 50) {
                Text(
                    text = "за одну сессию — 50 карточек",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            Text("🎉", style = MaterialTheme.typography.displayMedium)
            Text("На сегодня всё повторено!", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Новые карточки появятся по расписанию повторений",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 340.dp)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (dueCount > 0) {
                LiquidGlass(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onStartDue),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Начать повторение")
                    }
                }
            }
            RandomCountSlider(
                count = randomCount,
                onChange = onRandomCountChange,
                modifier = Modifier.fillMaxWidth(),
            )
            GlassPillButton(
                text = "Повторить $effectiveRandom " +
                    plural(effectiveRandom, "случайное слово", "случайных слова", "случайных слов"),
                onClick = onStartRandom,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Кнопка ответа «Помню/Не помню» на жидком стекле, с цветной иконкой и текстом. */
@Composable
private fun AnswerButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlass(
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(8.dp))
            Text(text, color = color)
        }
    }
}

/** Вторичная кнопка на жидком стекле (pill), без иконки. */
@Composable
private fun GlassPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiquidGlass(
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Ползунок количества случайных слов: 10..50 с шагом 10. На стеклянной панели. */
@Composable
private fun RandomCountSlider(count: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    LiquidGlass(shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Slider(
                value = count.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = 10f..50f,
                steps = 3, // промежуточные засечки: 20, 30, 40
                colors = SliderDefaults.colors(
                    thumbColor = AccentViolet,
                    activeTrackColor = AccentViolet,
                    inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(10, 20, 30, 40, 50).forEach {
                    Text(
                        text = "$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultState(
    correct: Int,
    answered: Int,
    onDone: () -> Unit,
) {
    val percent = if (answered > 0) correct * 100 / answered else 0
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Готово!", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Правильно: $correct из $answered ($percent%)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LiquidGlass(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onDone),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Отлично", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun EmptyState(onGoToWords: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Словарь пуст", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Добавь слова, которые хочешь выучить",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onGoToWords) { Text("Добавить слова") }
    }
}
