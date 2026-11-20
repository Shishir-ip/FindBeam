package com.findbeam.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.max

/** Mini RSSI history sparkline. */
@Composable
fun Sparkline(
    history: List<Int>,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        if (history.size < 2) {
            drawLine(
                color = color.copy(alpha = 0.25f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2f
            )
            return@Canvas
        }
        val min = history.min().toFloat() - 2f
        val max = history.max().toFloat() + 2f
        val range = max(max - min, 1f)
        val n = history.size

        fun x(i: Int) = (i.toFloat() / (n - 1).coerceAtLeast(1)) * size.width
        fun y(v: Int) = (1f - (v - min) / range) * size.height * 0.92f + size.height * 0.04f

        val line = Path()
        history.forEachIndexed { i, v ->
            if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v))
        }

        // Area fill
        val area = Path().apply {
            addPath(line)
            lineTo(x(n - 1), size.height)
            lineTo(x(0), size.height)
            close()
        }
        drawPath(area, color = color.copy(alpha = 0.10f))
        drawPath(
            line,
            color = color.copy(alpha = 0.85f),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        // Head dot
        drawCircle(
            color = color,
            radius = 7f,
            center = Offset(x(n - 1), y(history.last()))
        )
    }
}
