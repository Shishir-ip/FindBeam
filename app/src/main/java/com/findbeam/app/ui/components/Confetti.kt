package com.findbeam.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.findbeam.app.ui.theme.Amber
import com.findbeam.app.ui.theme.Danger
import com.findbeam.app.ui.theme.LocalAppColors
import com.findbeam.app.ui.theme.Neon
import kotlin.math.sin
import kotlin.random.Random

/**
 * Celebration burst used when the lost object is found. Self-disposing:
 * renders while [visible], animates ~2.8 s, then fades.
 */
@Composable
fun ConfettiOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    val app = LocalAppColors.current
    val palette = listOf(app.neon, Color.White, app.danger, Amber)

    data class P(
        val x: Float, val delay: Float, val fall: Float,
        val size: Float, val color: Color, val spin: Float, val wobble: Float
    )

    val parts = remember {
        val rnd = Random(2024)
        List(90) { i ->
            P(
                x = rnd.nextFloat(),
                delay = rnd.nextFloat() * 0.45f,
                fall = 0.9f + rnd.nextFloat() * 0.5f,
                size = 6f + rnd.nextFloat() * 10f,
                color = palette[i % palette.size],
                spin = (rnd.nextFloat() - 0.5f) * 3f,
                wobble = rnd.nextFloat() * 6f
            )
        }
    }

    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (t < 1f) {
            withFrameNanos { now ->
                t = ((now - start) / 2_800_000_000f).coerceIn(0f, 1f)
            }
        }
    }

    Canvas(modifier = modifier) {
        val fade = if (t > 0.72f) ((1f - t) / 0.28f).coerceIn(0f, 1f) else 1f
        for (p in parts) {
            val prog = (t * 1.55f - p.delay).coerceIn(0f, 1f)
            if (prog <= 0f || fade <= 0f) continue
            val y = -50f + size.height * prog * p.fall
            val x = p.x * size.width + sin(prog * 10f + p.wobble) * 30f
            rotate(degrees = prog * 360f * p.spin, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = fade),
                    topLeft = Offset(x - p.size / 2f, y - p.size / 2f),
                    size = Size(p.size, p.size * 0.62f)
                )
            }
        }
    }
}
