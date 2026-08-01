package com.example.cardapp.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/**
 * Слой с меш-фоном приложения, доступный стеклянным поверхностям как backdrop
 * (то, что они размывают и преломляют). Проставляется в [AppBackground].
 */
val LocalBackdrop = staticCompositionLocalOf<GraphicsLayer?> { null }

/**
 * Включено ли жидкое стекло (переключатель в меню). Выключено — поверхности
 * становятся непрозрачными, без преломления/бликов/прозрачности. Фон-меш
 * остаётся в любом случае.
 */
val LocalLiquidGlassEnabled = staticCompositionLocalOf { true }

/** «Жидкое стекло» доступно только с Android 13 (RuntimeShader/AGSL). */
val liquidGlassSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * AGSL-шейдер жидкого стекла: получает уже размытый фон в `backdrop` и
 * - у краёв преломляет его (эффект линзы: чем ближе к краю, тем сильнее
 *   смещается выборка внутрь),
 * - добавляет светлый блик по самому контуру.
 * Размытие делает не сам шейдер, а цепочка RenderEffect до него.
 */
private const val LIQUID_GLASS_AGSL = """
uniform shader backdrop;
uniform float2 size;

half4 main(float2 coord) {
    float2 uv = coord / size;
    // Расстояние до ближайшего края в долях (0 у края, 0.5 в центре).
    float2 toEdge = min(uv, 1.0 - uv);
    float edge = min(toEdge.x, toEdge.y);

    // Направление внутрь от ближайшего края.
    float2 n = float2(uv.x < 0.5 ? 1.0 : -1.0, uv.y < 0.5 ? 1.0 : -1.0);
    // Преломление нарастает у края (полоса ~18% ширины).
    float refract = smoothstep(0.18, 0.0, edge);
    float2 offset = n * refract * refract * 22.0;

    half4 col = backdrop.eval(coord + offset);

    // Светлый ободок по контуру.
    float rim = smoothstep(0.045, 0.0, edge);
    col.rgb += rim * 0.22;
    // Лёгкое общее осветление, чтобы стекло «жило».
    col.rgb += 0.03;
    return col;
}
"""

/**
 * Стеклянная поверхность. На Android 13+ — настоящее жидкое стекло: размывает
 * и преломляет меш-фон под собой + блик по краю. На старых устройствах и без
 * backdrop — обычная полупрозрачная плашка с тонким ободком (тот же вид, что
 * был раньше).
 *
 * [blurRadius] — сила матовости, [tintAlpha] — плотность цветного налёта.
 */
@Composable
fun LiquidGlass(
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    blurRadius: Float = 20f,
    tintAlpha: Float = 0.14f,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!LocalLiquidGlassEnabled.current) {
        Box(
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            content = content,
        )
        return
    }

    val backdrop = LocalBackdrop.current
    val tint = MaterialTheme.colorScheme.surface.copy(alpha = tintAlpha)
    val rim = Color.White.copy(alpha = 0.18f)

    if (backdrop == null || !liquidGlassSupported) {
        // Фоллбэк — прежнее матовое стекло.
        Box(
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), shape),
            content = content,
        )
        return
    }

    val shader = remember { RuntimeShader(LIQUID_GLASS_AGSL) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(shape),
    ) {
        // Слой стекла: рисует фон, смещённый под этот элемент, и прогоняет его
        // через blur → шейдер преломления. Контент карточки лежит поверх и
        // эффекта не касается.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    shader.setFloatUniform("size", size.width, size.height)
                    renderEffect = RenderEffect.createChainEffect(
                        RenderEffect.createRuntimeShaderEffect(shader, "backdrop"),
                        RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP),
                    ).asComposeRenderEffect()
                }
                .drawBehind {
                    translate(-origin.x, -origin.y) { drawLayer(backdrop) }
                },
        )
        // Цветной налёт + ободок поверх преломлённого фона.
        Box(
            Modifier
                .matchParentSize()
                .background(tint)
                .border(1.dp, rim, shape),
        )
        content()
    }
}
