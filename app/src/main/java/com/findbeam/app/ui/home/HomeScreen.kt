package com.findbeam.app.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.findbeam.app.core.haptics.Haptics
import com.findbeam.app.ui.components.FramedCard
import com.findbeam.app.ui.components.PermissionKind
import com.findbeam.app.ui.components.PermissionRationaleDialog
import com.findbeam.app.ui.components.SectionLabel
import com.findbeam.app.ui.components.rememberMissingPermissions
import com.findbeam.app.ui.theme.LocalAppColors

@Composable
fun HomeScreen(
    onOpenFinder: () -> Unit,
    onOpenWifi: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val app = LocalAppColors.current
    val context = LocalContext.current
    val missing = rememberMissingPermissions()
    var rationale: PermissionKind? by remember { mutableStateOf(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it.values.any { granted -> granted }) Haptics.tick(context, 160)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // ---- Header ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(app.neon.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(app.neon, RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "FindBeam",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = app.textDim
                )
            }
        }

        Text(
            "Find lost things. Find the best Wi-Fi.",
            style = MaterialTheme.typography.bodyLarge,
            color = app.textDim,
            modifier = Modifier.padding(top = 8.dp, bottom = 26.dp)
        )

        // ---- Feature cards ----
        FeatureCard(
            title = "Object Finder",
            subtitle = "Point a live arrow at your lost earbuds, watch or tracker using Bluetooth and the compass.",
            icon = Icons.Rounded.Bluetooth,
            accent = app.neon,
            animation = { RadarAnimation(Modifier.size(84.dp), app.neon) },
            onClick = onOpenFinder
        )
        Spacer(Modifier.height(16.dp))
        FeatureCard(
            title = "Wi-Fi Best Spot",
            subtitle = "Walk around and watch a live meter find where your Wi-Fi is strongest. Save spots and compare.",
            icon = Icons.Rounded.Wifi,
            accent = app.danger,
            animation = { WifiBarsAnimation(Modifier.size(84.dp), app.danger) },
            onClick = onOpenWifi
        )

        // ---- Permissions ----
        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Get ready")
            Spacer(Modifier.height(10.dp))
            FramedCard {
                Text(
                    "FindBeam needs a few permissions to do its job.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))
                missing.forEach { kind ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                kind.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Button(
                            onClick = { rationale = kind },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = app.neon.copy(alpha = 0.14f),
                                contentColor = app.neon
                            )
                        ) {
                            Text("Allow")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = app.textFaint,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Works offline · No account · No ads · No data collection",
                style = MaterialTheme.typography.labelSmall,
                color = app.textFaint
            )
        }
        Spacer(Modifier.height(32.dp))
    }

    rationale?.let { kind ->
        PermissionRationaleDialog(
            kind = kind,
            onDismiss = { rationale = null },
            onConfirm = {
                rationale = null
                launcher.launch(
                    com.findbeam.app.ui.components.permissionsFor(kind).toTypedArray()
                )
            }
        )
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    animation: @Composable (Modifier) -> Unit,
    onClick: () -> Unit
) {
    val app = LocalAppColors.current
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                Haptics.tick(context, 140)
                onClick()
            },
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, app.border)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = app.textDim
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Open",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Box(contentAlignment = Alignment.Center) {
                animation(Modifier.size(96.dp))
            }
        }
    }
}

/** Expanding radar rings for the Object Finder card. */
@Composable
fun RadarAnimation(modifier: Modifier, accent: Color) {
    val transition = rememberInfiniteTransition(label = "radar")
    val s1 by transition.animateFloat(
        0.15f, 1f,
        infiniteRepeatable(tween(1700, easing = LinearEasing)),
        label = "s1"
    )
    val s2 by transition.animateFloat(
        0.15f, 1f,
        infiniteRepeatable(tween(1700, delayMillis = 550, easing = LinearEasing)),
        label = "s2"
    )
    val a by transition.animateFloat(
        0.5f, 0f,
        infiniteRepeatable(tween(1700, easing = LinearEasing)),
        label = "a"
    )
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        drawCircle(accent.copy(alpha = 0.10f), radius = r, center = c)
        drawCircle(
            accent.copy(alpha = a),
            radius = r * s1,
            center = c,
            style = Stroke(width = 3f)
        )
        drawCircle(
            accent.copy(alpha = a * 0.7f),
            radius = r * s2,
            center = c,
            style = Stroke(width = 3f)
        )
        drawCircle(accent, radius = 7f, center = c)
    }
}

/** Rising bars for the Wi-Fi card. */
@Composable
fun WifiBarsAnimation(modifier: Modifier, accent: Color) {
    val transition = rememberInfiniteTransition(label = "bars")
    val b1 by transition.animateFloat(
        0.2f, 1f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val b2 by transition.animateFloat(
        0.2f, 1f,
        infiniteRepeatable(tween(900, delayMillis = 180, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val b3 by transition.animateFloat(
        0.2f, 1f,
        infiniteRepeatable(tween(900, delayMillis = 360, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
    )
    Canvas(modifier) {
        val barW = size.width / 5f
        val gap = barW * 0.35f
        val heights = listOf(0.45f, 0.7f, 1f)
        val anims = listOf(b1, b2, b3)
        for (i in heights.indices) {
            val h = size.height * heights[i] * anims[i]
            drawRoundRect(
                color = accent.copy(alpha = 0.85f),
                topLeft = androidx.compose.ui.geometry.Offset(i * (barW + gap), size.height - h),
                size = androidx.compose.ui.geometry.Size(barW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2)
            )
        }
    }
}
