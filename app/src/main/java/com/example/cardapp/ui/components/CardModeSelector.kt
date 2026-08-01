package com.example.cardapp.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cardapp.ui.CardMode
import com.example.cardapp.ui.MODE_HL_DURATION_MS
import com.example.cardapp.ui.ModeHighlight
import com.example.cardapp.ui.cardModeEase
import kotlinx.coroutines.launch

/**
 * Три кнопки режима карточек с подсветкой выбранного, которая плавно съезжает
 * на нажатую кнопку.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CardModeSelector(
    selected: CardMode,
    highlight: ModeHighlight,
    onSelect: (CardMode) -> Unit,
    onElapsed: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var elapsed by remember(highlight.start, highlight.target) {
        mutableLongStateOf(highlight.elapsedMs)
    }

    LaunchedEffect(highlight.start, highlight.target) {
        val gen = highlight.gen
        var last = withFrameNanos { it }
        while (elapsed < MODE_HL_DURATION_MS) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000L).coerceIn(0L, 50L)
            last = now
            elapsed = (elapsed + dt).coerceAtMost(MODE_HL_DURATION_MS)
            onElapsed(elapsed, gen)
        }
    }

    val f = cardModeEase(elapsed.toFloat() / MODE_HL_DURATION_MS)
    val pos = highlight.start + (highlight.target - highlight.start) * f
    val highlightColor = Color(0xFFBFD8FF).copy(alpha = 0.24f)

    LiquidGlass(
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
    ) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val buttonWidth = maxWidth / CardMode.entries.size
        androidx.compose.foundation.layout.Box(
            Modifier
                .offset(x = buttonWidth * pos)
                .width(buttonWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(highlightColor),
        )
        val scope = rememberCoroutineScope()
        val haptics = LocalHapticFeedback.current
        Row(Modifier.fillMaxSize()) {
            CardMode.entries.forEach { mode ->
                // Удержание показывает подсказку о режиме и НЕ переключает его:
                // combinedClickable с onLongClick поглощает жест, onClick после
                // долгого нажатия не срабатывает. Переключение — только тапом.
                val tooltipState = rememberTooltipState()
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = {
                            PlainTooltip(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    text = when (mode) {
                                        CardMode.MAIN -> "Слово спереди, перевод на обороте"
                                        CardMode.REVERSE -> "Перевод спереди, слово на обороте"
                                        CardMode.COMBO -> "Вперемешку: часть карточек словом вперёд, часть — переводом"
                                    },
                                    textAlign = TextAlign.Center,
                                )
                            }
                        },
                        state = tooltipState,
                        enableUserInput = false,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { if (enabled) onSelect(mode) },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        scope.launch { tooltipState.show() }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when (mode) {
                                    CardMode.MAIN -> "Основной"
                                    CardMode.REVERSE -> "Реверс"
                                    CardMode.COMBO -> "Комбо"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (mode == selected) FontWeight.SemiBold
                                             else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                color = if (mode == selected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
      }
    }
}
