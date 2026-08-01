package com.example.cardapp.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Карточка с анимацией переворота вокруг вертикальной оси. Что показано на
 * лицевой и обратной стороне, задаётся снаружи (зависит от режима карточек).
 *
 * @param frontText текст-подсказка на лицевой стороне
 * @param backText ответ на обороте
 * @param backLabel мелкая подпись над ответом («перевод», «слово»)
 */
@Composable
fun FlipCard(
    frontText: String,
    backText: String,
    backLabel: String,
    flipped: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "flip",
    )
    // После 90° показываем обратную сторону (и «отзеркаливаем» её обратно,
    // иначе текст будет отображаться задом наперёд).
    val showBack = rotation > 90f
    val multiline = { s: String -> s.contains('\n') }

    val cardShape = RoundedCornerShape(24.dp)
    val glow = Color(0xFFBFD8FF)
    // Свечение и обводка — только при включённом жидком стекле.
    val liquid = LocalLiquidGlassEnabled.current
    Card(
        onClick = onFlip,
        modifier = modifier
            // Трансформация переворота — ПЕРВОЙ, чтобы свечение и обводка
            // вращались вместе с карточкой и исчезали, когда она встаёт ребром
            // (иначе свечение оставалось полным прямоугольником за карточкой).
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .then(
                if (liquid) Modifier.shadow(
                    elevation = 14.dp,
                    shape = cardShape,
                    ambientColor = glow,
                    spotColor = glow,
                ) else Modifier,
            )
            .then(
                if (liquid) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.5f), cardShape)
                else Modifier,
            ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = if (liquid) 0.dp else 6.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                if (showBack) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationY = if (showBack) 180f else 0f },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                if (showBack) {
                    Text(
                        text = backLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = backText,
                        style = if (multiline(backText)) MaterialTheme.typography.headlineSmall
                                else MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = frontText,
                        style = if (multiline(frontText)) MaterialTheme.typography.headlineSmall
                                else MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "нажми, чтобы перевернуть",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
