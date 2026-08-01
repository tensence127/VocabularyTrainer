package com.example.cardapp.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

@Composable
fun AvatarCropper(
    source: Bitmap,
    onConfirm: (android.graphics.Rect) -> Unit,
    onCancel: () -> Unit,
) {
    val image = remember(source) { source.asImageBitmap() }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val radius = minOf(widthPx, heightPx) / 2f - with(density) { 24.dp.toPx() }
            val center = Offset(widthPx / 2f, heightPx / 2f)
            val w = source.width.toFloat()
            val h = source.height.toFloat()

            // Минимальный масштаб — фото целиком накрывает круг; максимальный —
            // чтобы в круг не попадал совсем уж крошечный кусок исходника.
            val minScale = 2f * radius / minOf(w, h)
            val maxScale = maxOf(minScale, 2f * radius / 256f)
            var scale by remember(minScale) { mutableFloatStateOf(minScale) }
            var offset by remember(minScale) { mutableStateOf(Offset.Zero) }

            // Круг не должен вылезать за фото.
            fun clampOffset(o: Offset, s: Float): Offset {
                val maxX = (s * w / 2f - radius).coerceAtLeast(0f)
                val maxY = (s * h / 2f - radius).coerceAtLeast(0f)
                return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(minScale, maxScale) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                            // Смещение масштабируется вместе с фото, чтобы
                            // точка в центре круга не «уплывала» при зуме.
                            val ratio = newScale / scale
                            scale = newScale
                            offset = clampOffset(offset * ratio + pan, newScale)
                        }
                    },
            ) {
                val dstW = w * scale
                val dstH = h * scale
                drawImage(
                    image = image,
                    dstOffset = IntOffset(
                        (center.x + offset.x - dstW / 2f).roundToInt(),
                        (center.y + offset.y - dstH / 2f).roundToInt(),
                    ),
                    dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt()),
                    filterQuality = FilterQuality.High,
                )
                // Затемнение всего, что не попадает в круг (дырка через
                // EvenOdd), и тонкая обводка круга.
                val outside = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addOval(
                        Rect(
                            center.x - radius,
                            center.y - radius,
                            center.x + radius,
                            center.y + radius,
                        )
                    )
                }
                drawPath(outside, Color.Black.copy(alpha = 0.6f))
                drawCircle(
                    color = Color.White.copy(alpha = 0.7f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onCancel) {
                    Text("Отмена", color = Color.White)
                }
                Button(onClick = {
                    // Центр круга в координатах исходника и половина стороны
                    // квадрата — обратное преобразование текущего вида.
                    val half = radius / scale
                    val cx = w / 2f - offset.x / scale
                    val cy = h / 2f - offset.y / scale
                    onConfirm(
                        android.graphics.Rect(
                            (cx - half).roundToInt().coerceAtLeast(0),
                            (cy - half).roundToInt().coerceAtLeast(0),
                            (cx + half).roundToInt().coerceAtMost(source.width),
                            (cy + half).roundToInt().coerceAtMost(source.height),
                        )
                    )
                }) {
                    Text("Готово")
                }
            }
        }
    }
}
