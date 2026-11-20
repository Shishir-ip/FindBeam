package com.findbeam.app.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.findbeam.app.ui.theme.Amber
import com.findbeam.app.ui.theme.Danger
import com.findbeam.app.ui.theme.LocalAppColors
import com.findbeam.app.ui.theme.Neon
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Circular signal gauge: 270° sweep from red (weak) through amber to neon
 * green (strong), with a soft glow on the leading edge.
 */
@Composable
fun SignalGauge(
    progress: Float,
    centerTop: String,
    centerUnit: String,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    val app = LocalAppColors.current
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 90f),
        label = "gauge"
    )

    val accent = when {
        animated < 0.5f -> lerp(Danger, Amber, animated * 2f)
        else -> lerp(Amber, Neon, (animated - 0.5f) * 2f)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val diameter = min(size.width, size.height) - stroke * 1.6f
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            val start = 135f
            val sweepMax = 270f
            val sweep = sweepMax * animated

            // Track
            drawArc(
                color = app.border,
                startAngle = start,
                sweepAngle = sweepMax,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )

            // Progress arc with glow
            if (sweep > 2f) {
                val glowPaint = Paint().apply {
                    this.color = accent.copy(alpha = 0.55f)
                    this.asFrameworkPaint().maskFilter =
                        BlurMaskFilter(stroke * 0.9f, BlurMaskFilter.Blur.NORMAL)
                }
                drawIntoCanvas { canvas ->
                    val rect = android.graphics.RectF(
                        topLeft.x, topLeft.y,
                        topLeft.x + diameter, topLeft.y + diameter
                    )
                    canvas.nativeCanvas.drawArc(
                        rect, start, sweep, false, glowPaint.asFrameworkPaint()
                    )
                }

                drawArc(
                    color = accent,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )

                // Leading-edge cap dot
                val endRad = Math.toRadians((start + sweep).toDouble())
                val capR = diameter / 2f
                val cx = topLeft.x + capR + capR * cos(endRad).toFloat()
                val cy = topLeft.y + capR + capR * sin(endRad).toFloat()
                drawCircle(accent, radius = stroke * 0.42f, center = Offset(cx, cy))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerTop,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = centerUnit,
                style = MaterialTheme.typography.labelMedium,
                color = app.textFaint
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
