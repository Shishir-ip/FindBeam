package com.findbeam.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.findbeam.app.ui.theme.LocalAppColors

/** A premium bordered card with a subtle top-edge sheen. */
@Composable
fun FramedCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = LocalAppColors.current.border,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/** Dim, letterspaced section heading. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalAppColors.current.textFaint,
        modifier = modifier
    )
}

/** Small pulsing dot used to convey "live". */
@Composable
fun LiveDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )
    Box(
        modifier
            .size(size)
            .background(color.copy(alpha = alpha), RoundedCornerShape(50))
    )
}

/** Icon + value + caption chip used across dashboards. */
@Composable
fun StatChip(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalAppColors.current.textDim
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = LocalAppColors.current.textFaint
            )
        }
    }
}

/** Number/text that slides up when its value changes. */
@Composable
fun AnimatedNumber(
    value: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (slideInVertically { it / 2 } + fadeIn())
                .togetherWith(slideOutVertically { -it / 2 } + fadeOut())
        },
        label = "number"
    ) { v ->
        Text(text = v, style = style, color = color, modifier = modifier)
    }
}

/** Empty / placeholder state. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = LocalAppColors.current.textFaint,
            modifier = Modifier.size(44.dp)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = LocalAppColors.current.textDim
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = LocalAppColors.current.textFaint,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
fun SpacerHeight(height: Dp) = Spacer(Modifier.height(height))
