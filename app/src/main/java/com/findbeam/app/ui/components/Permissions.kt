package com.findbeam.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.findbeam.app.ui.theme.LocalAppColors
import kotlin.math.cos
import kotlin.math.sin

/** The three permission groups FindBeam asks for, with honest explanations. */
enum class PermissionKind(
    val title: String,
    val explanation: String
) {
    BLUETOOTH(
        "Bluetooth",
        "FindBeam uses Bluetooth to discover nearby devices and measure their signal " +
            "strength, so it can guide you to your lost item. Scanning happens entirely " +
            "on your phone — results never leave it."
    ),
    LOCATION(
        "Location",
        "Android requires location permission for Bluetooth and Wi-Fi scanning. " +
            "FindBeam never works out where you are from it — your location is not " +
            "read, stored or shared."
    ),
    MOTION(
        "Motion & activity",
        "Motion access powers the step counter that shows how far you've walked " +
            "while searching. It is read locally and never saved or shared."
    )
}

fun permissionsFor(kind: PermissionKind): List<String> = when (kind) {
    PermissionKind.BLUETOOTH ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
    PermissionKind.LOCATION ->
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    PermissionKind.MOTION ->
        listOf(Manifest.permission.ACTIVITY_RECOGNITION)
}

fun computeMissingPermissions(context: Context): List<PermissionKind> {
    val missing = mutableListOf<PermissionKind>()
    for (kind in PermissionKind.entries) {
        val granted = permissionsFor(kind).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (!granted) missing.add(kind)
    }
    return missing
}

/** Missing permission groups, refreshed automatically on every resume. */
@Composable
fun rememberMissingPermissions(): List<PermissionKind> {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(computeMissingPermissions(context)) }
    LifecycleResumeEffect(Unit) {
        missing = computeMissingPermissions(context)
        onPauseOrDispose { }
    }
    return missing
}

@Composable
fun PermissionRationaleDialog(
    kind: PermissionKind,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kind.title) },
        text = {
            Text(
                kind.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalAppColors.current.textDim
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Allow") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        }
    )
}

/**
 * Banner prompting figure-8 compass calibration, with a live figure-8
 * animation showing the motion.
 */
@Composable
fun CompassCalibrationBanner(modifier: Modifier = Modifier) {
    val app = LocalAppColors.current
    val transition = rememberInfiniteTransition(label = "figure8")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2400, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "phase"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, app.border)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Canvas(modifier = Modifier.size(width = 64.dp, height = 48.dp)) {
                // Figure-8 path: x = sin(θ), y = sin(θ)·cos(θ)
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val path = Path()
                var first = true
                for (i in 0..120) {
                    val th = i / 120.0 * 2.0 * Math.PI
                    val x = cx + (sin(th) * 0.42 * w).toFloat()
                    val y = cy + (sin(th) * cos(th) * 0.8 * h).toFloat()
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                }
                drawPath(
                    path = path,
                    color = app.textFaint.copy(alpha = 0.5f),
                    style = Stroke(width = 3f)
                )
                // Travelling dot
                val th = phase * 2.0 * Math.PI
                val dx = cx + (sin(th) * 0.42 * w).toFloat()
                val dy = cy + (sin(th) * cos(th) * 0.8 * h).toFloat()
                drawCircle(app.neon, radius = 9f, center = Offset(dx, dy))
            }
            Column {
                Text(
                    "Compass needs calibration",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Move your phone in a slow figure-8 motion for a few seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = app.textDim
                )
            }
        }
    }
}
