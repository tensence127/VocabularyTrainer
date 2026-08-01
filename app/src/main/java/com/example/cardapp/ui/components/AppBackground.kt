package com.example.cardapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

private val MeshBase = Color(0xFF3A2A6B)
private val MeshPurple = Color(0xFF7C3AED)
private val MeshBlue = Color(0xFF4AA8E8)
private val MeshGreen = Color(0xFF3E9B4E)
private val MeshWarm = Color(0xFFF6E7A0)

// Затемняющая вуаль поверх меша.
private val Veil = Color(0xFF070512).copy(alpha = 0.55f)

/**
 * Фоновый меш-градиент под всем содержимым приложения.
 * Меш пишется в отдельный слой ([LocalBackdrop]) — чтобы стеклянные
 * поверхности могли брать его как backdrop и преломлять (см. [LiquidGlass]).
 */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val backdrop = rememberGraphicsLayer()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    backdrop.record { drawMesh() }
                    drawLayer(backdrop)
                },
        )
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Surface(
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                content()
            }
        }
    }
}

/** Рисует меш: база, цветовые пятна (снизу вверх), затемняющая вуаль. */
private fun DrawScope.drawMesh() {
    drawRect(MeshBase)
    spot(MeshPurple, 0.16f, 0.14f, 0.62f)
    spot(MeshBlue, 0.90f, 0.18f, 0.62f)
    spot(MeshGreen, 0.28f, 0.94f, 0.62f)
    spot(MeshWarm, 0.92f, 0.86f, 0.62f)
    drawRect(Veil)
}

/** Одно цветовое пятно меша: сплошной цвет в центре, прозрачность к краям. */
private fun DrawScope.spot(color: Color, fx: Float, fy: Float, radiusFactor: Float) {
    val center = Offset(size.width * fx, size.height * fy)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = size.maxDimension * radiusFactor,
        ),
    )
}
