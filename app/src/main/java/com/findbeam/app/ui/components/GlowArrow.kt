package com.findbeam.app.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.findbeam.app.ui.theme.LocalAppColors
import kotlin.math.min

/**
 * The big guidance arrow. Rotates smoothly (parent passes the animated
 * angle), glows neon green when aligned and turns red when the direction is
 * wrong. Two radar rings pulse behind it.
 */
@Composable
fun GlowArrow(
    angleDeg: Float,
    aligned: Boolean,
    arrived: Boolean,
    modifier: Modifier = Modifier,
    arrowSize: Dp = 250.dp
) {
    val app = LocalAppColors.current
    val color by animateColorAsState(
        targetValue = if (aligned || arrived) app.neon else app.danger,
        animationSpec = tween(280),
        label = "arrowColor"
    )

    val transition = rememberInfiniteTransition(label = "rings")
    val ring1 by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing)),
        label = "ring1"
    )
    val ring2 by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Restart),
        label = "ring2"
    )
    val ringAlpha by transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing)),
        label = "ringAlpha"
    )

    Box(modifier = modifier.size(arrowSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseR = min(this.size.width, this.size.height) / 2f * 0.72f

            // Radar rings
            listOf(ring1, ring2).forEachIndexed { i, s ->
                drawCircle(
                    color = color.copy(alpha = ringAlpha * (if (i == 0) 1f else 0.6f)),
                    radius = baseR * s,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            val arrow = Path().apply {
                moveTo(center.x, center.y - baseR * 0.95f)
                lineTo(center.x + baseR * 0.62f, center.y + baseR * 0.72f)
                lineTo(center.x, center.y + baseR * 0.30f)
                lineTo(center.x - baseR * 0.62f, center.y + baseR * 0.72f)
                close()
            }

            rotate(degrees = angleDeg, pivot = center) {
                // Glow
                val glow = Paint().apply {
                    this.color = color.copy(alpha = 0.5f)
                    this.asFrameworkPaint().maskFilter =
                        BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawPath(
                        arrow.asAndroidPath(), glow.asFrameworkPaint()
                    )
                }
                // Body
                drawPath(arrow, color = color)
                // Crisp white outline for definition on any background
                drawPath(
                    arrow,
                    color = Color.White.copy(alpha = 0.35f),
                    style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
                )
            }
        }
    }
}
