package com.example.cardapp.ui.components

import androidx.compose.animation.core.Easing
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Быстрые переходы: открытие экрана и жест «назад». */
const val SLIDE_FAST_MS = 220

/** Медленнее — перелистывание вкладок нижней панелью. */
const val SLIDE_SLOW_MS = 320

/**
 * Кривая переходов экранов: кубический ease-out (старт с полной скорости,
 * мягкое торможение). Та же формула, что в [SlideAnim.fraction] — вынесена
 * как Easing, чтобы подсветка нижней навигации ехала синхронно с экраном.
 */
val SlideEaseOut = Easing { t ->
    val u = 1f - t
    1f - u * u * u
}

/** Максимальный шаг прогресса за кадр — как у карточки-награды и подсветки режима. */
private const val MAX_FRAME_STEP_MS = 50L

/** Номинальный кадр: первый тик после перезапуска сразу двигает анимацию. */
private const val NOMINAL_FRAME_MS = 16L

/**
 * Снимок одного перехода: пара экранов, направление, длительность и стартовые
 * позиции (смещение в ширинах контейнера + прозрачность). Прогресс живёт
 * внутри снимка, поэтому «какая анимация» и «сколько проиграно» меняются
 * атомарно — рассинхрона при спаме нет (тот же приём, что у ModeHighlight).
 */
private class SlideAnim<T>(
    val previous: T?,
    val current: T,
    /** Откуда въезжает current: +1 — справа, -1 — слева. previous уходит в -dir. */
    val dir: Int,
    val durationMs: Int,
    val prevStartOffset: Float,
    val prevStartAlpha: Float,
    val curStartOffset: Float,
    val curStartAlpha: Float,
) {
    /** Накопленный по кадрам прогресс, мс. */
    val elapsedMs = mutableLongStateOf(0L)

    /**
     * Доля анимации 0..1, кубический ease-out: старт с полной скорости,
     * мягкое торможение. Кривая одна и для обычного перехода, и для
     * прерванного — smoothstep на обычных ощущался тягучим рядом с быстрым
     * стартом прерванных, а у прерванных разгон с нуля выглядел бы как
     * мгновенная остановка летящей картинки.
     */
    fun fraction(): Float {
        if (durationMs <= 0) return 1f
        val t = (elapsedMs.longValue.toFloat() / durationMs).coerceIn(0f, 1f)
        val u = 1f - t
        return 1f - u * u * u
    }
}

private class SlideHolder<T>(var anim: SlideAnim<T>)

@Composable
fun <T> SlideContent(
    target: T,
    modifier: Modifier = Modifier,
    duration: (from: T, to: T) -> Int = { _, _ -> SLIDE_FAST_MS },
    forward: (from: T, to: T) -> Boolean,
    content: @Composable (T) -> Unit,
) {
    val holder = remember {
        SlideHolder(SlideAnim(null, target, 1, 0, 0f, 1f, 0f, 1f))
    }
    val anim = remember(target) {
        val old = holder.anim
        if (target != old.current) {
            val p = Snapshot.withoutReadObservation { old.fraction() }
            val dir = if (forward(old.current, target)) 1 else -1
            val comeback = target == old.previous && p < 1f
            val curStartOffset =
                if (comeback) lerp(old.prevStartOffset, -old.dir.toFloat(), p) else dir.toFloat()
            holder.anim = SlideAnim(
                previous = old.current,
                current = target,
                dir = dir,
                durationMs = (duration(old.current, target) * abs(curStartOffset)).roundToInt(),
                prevStartOffset = lerp(old.curStartOffset, 0f, p),
                prevStartAlpha = lerp(old.curStartAlpha, 1f, p),
                curStartOffset = curStartOffset,
                curStartAlpha = if (comeback) lerp(old.prevStartAlpha, 0f, p) else 0f,
            )
        }
        holder.anim
    }

    LaunchedEffect(anim) {
        var last = 0L
        while (anim.elapsedMs.longValue < anim.durationMs) {
            val now = withFrameNanos { it }
            val dt =
                if (last == 0L) NOMINAL_FRAME_MS
                else ((now - last) / 1_000_000L).coerceIn(0L, MAX_FRAME_STEP_MS)
            last = now
            anim.elapsedMs.longValue =
                (anim.elapsedMs.longValue + dt).coerceAtMost(anim.durationMs.toLong())
        }
    }

    val running by remember(anim) {
        derivedStateOf { anim.previous != null && anim.elapsedMs.longValue < anim.durationMs }
    }

    Box(modifier) {
        val prev = anim.previous.takeIf { running }
        val states = if (prev != null) listOf(prev, anim.current) else listOf(anim.current)
        for (state in states) {
            key(state) {
                val isCurrent = state == anim.current
                Box(
                    if (isCurrent) {
                        Modifier.graphicsLayer {
                            val p = anim.fraction()
                            translationX = size.width * lerp(anim.curStartOffset, 0f, p)
                            alpha = lerp(anim.curStartAlpha, 1f, p)
                        }
                    } else {
                        Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                val p = anim.fraction()
                                translationX =
                                    size.width * lerp(anim.prevStartOffset, -anim.dir.toFloat(), p)
                                alpha = lerp(anim.prevStartAlpha, 0f, p)
                            }
                    },
                ) {
                    content(state)
                }
            }
        }
    }
}
