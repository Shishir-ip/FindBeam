package com.findbeam.app.ui.wifi

import android.view.WindowManager
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findbeam.app.core.signal.SignalMath
import com.findbeam.app.core.store.Locator
import com.findbeam.app.core.wifi.WifiTracker
import com.findbeam.app.ui.components.SignalGauge
import com.findbeam.app.ui.components.SectionLabel
import com.findbeam.app.ui.theme.Amber
import com.findbeam.app.ui.theme.LocalAppColors
import com.findbeam.app.util.Format
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun WifiScreen(
    viewModel: WifiViewModel,
    onBack: () -> Unit,
    onOpenSpots: () -> Unit
) {
    val app = LocalAppColors.current
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }
    DisposableEffect(Unit) { onDispose { viewModel.stop() } }

    // Keep screen awake while measuring.
    val keepOn = Locator.settingsStore.settings.collectAsState().value.keepScreenOn
    DisposableEffect(keepOn) {
        val window = (context as? android.app.Activity)?.window
        if (keepOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ---- Top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "Wi-Fi Best Spot",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.toggleSound() }) {
                Icon(
                    if (ui.soundOn) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                    contentDescription = "Toggle tone",
                    tint = if (ui.soundOn) app.neon else app.textFaint
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(6.dp))

            if (!ui.wifiEnabled) {
                NoWifiBanner()
                Spacer(Modifier.height(16.dp))
            }

            // ---- Big live gauge ----
            val state = ui.state
            SignalGauge(
                progress = state?.normalized ?: 0f,
                centerTop = if (state?.hasWifi == true) "${state.rssi}" else "—",
                centerUnit = "dBm live",
                modifier = Modifier.size(210.dp),
                caption = state?.let {
                    when {
                        it.normalized >= 0.8f -> "Excellent"
                        it.normalized >= 0.6f -> "Good"
                        it.normalized >= 0.35f -> "Fair"
                        else -> "Weak"
                    }
                }
            )

            Spacer(Modifier.height(14.dp))

            // ---- Network info chips ----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoChip(Icons.Rounded.Wifi, state?.ssid?.ifBlank { "Unknown network" } ?: "—")
                InfoChip(Icons.Rounded.NetworkCheck, state?.let { WifiTracker.bandLabel(it.band) } ?: "—")
                InfoChip(Icons.Rounded.Speed, state?.let { "${it.linkSpeedMbps} Mbps" } ?: "—")
            }

            Spacer(Modifier.height(18.dp))

            // ---- Best so far ----
            val bestLevel = SignalMath.strength01(ui.bestRssi.toFloat())
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = app.neon.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, app.neon.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Star,
                        null,
                        tint = app.neon,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Best spot so far",
                            style = MaterialTheme.typography.labelSmall,
                            color = app.textFaint
                        )
                        Text(
                            if (ui.bestRssi > -99) Format.rssi(ui.bestRssi) else "walk around to find it",
                            style = MaterialTheme.typography.titleMedium,
                            color = app.neon
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // mini bar
                    Box(
                        Modifier
                            .width(72.dp)
                            .height(6.dp)
                            .background(app.border, RoundedCornerShape(6.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.1f + 0.9f * bestLevel)
                                .height(6.dp)
                                .background(app.neon, RoundedCornerShape(6.dp))
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ---- Hold to save ----
            HoldToSaveButton(
                saving = ui.saving,
                progress = ui.saveProgress,
                stage = ui.saveStage,
                enabled = (ui.state?.hasWifi == true) && !ui.saving && ui.pendingSave == null,
                onComplete = { viewModel.beginSaveSpot() }
            )

            if (ui.saveStage.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = app.neon
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${ui.saveStage}…",
                        style = MaterialTheme.typography.labelMedium,
                        color = app.textDim
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            // ---- Saved spots link ----
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, app.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSpots() }
                ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Saved spots",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Ranked best to worst — pick where to put your desk or router",
                            style = MaterialTheme.typography.bodySmall,
                            color = app.textFaint
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        null,
                        tint = app.neon,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(26.dp))

            // ---- Nearby networks ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Networks in range", modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.refreshNetworks() }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.Refresh, "Rescan", tint = app.textDim, modifier = Modifier.size(18.dp))
                }
            }

            if (ui.networks.isEmpty()) {
                Text(
                    if (ui.networksScanning) "Scanning…" else "Tap refresh to scan nearby networks",
                    style = MaterialTheme.typography.bodySmall,
                    color = app.textFaint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.networks.take(12).forEach { net ->
                        NetworkRow(net)
                    }
                }
            }

            Spacer(Modifier.height(34.dp))
        }
    }

    // ---- Save dialog ----
    ui.pendingSave?.let { pending ->
        SaveSpotDialog(
            pending = pending,
            onDismiss = { viewModel.dismissSave() },
            onSave = { viewModel.confirmSave(it) }
        )
    }
}


@Composable
private fun NoWifiBanner() {
    val app = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = app.danger.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, app.danger.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Close, null, tint = app.danger, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Wi-Fi is off. Turn it on to measure signal strength.",
                style = MaterialTheme.typography.bodySmall,
                color = app.danger
            )
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val app = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, app.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = app.textDim, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = app.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HoldToSaveButton(
    saving: Boolean,
    progress: Float,
    stage: String,
    enabled: Boolean,
    onComplete: () -> Unit
) {
    val app = LocalAppColors.current
    val context = LocalContext.current
    var holding by remember { mutableStateOf(false) }
    val animatedHold by animateFloatAsState(
        if (holding) 1f else 0f,
        spring(dampingRatio = 0.9f, stiffness = 60f),
        label = "hold"
    )
    val shownProgress = if (saving) progress else animatedHold

    Box(contentAlignment = Alignment.Center) {
        // progress ring behind label
        CircularProgressIndicator(
            progress = { shownProgress.coerceIn(0f, 1f) },
            modifier = Modifier.size(210.dp),
            strokeWidth = 8.dp,
            color = app.neon,
            trackColor = Color.Transparent,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        Surface(
            shape = CircleShape,
            color = if (saving) app.neon.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (saving || holding) app.neon else app.border),
            modifier = Modifier
                .size(178.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onPress = {
                        holding = true
                        val completed = withTimeoutOrNull(2600) {
                            tryAwaitRelease()
                            false
                        } == null
                        holding = false
                        if (completed) {
                            onComplete()
                        }
                    })
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    when {
                        saving -> "Measuring…"
                        holding -> "Keep holding"
                        else -> "Save this spot"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (saving || holding) app.neon else MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (saving) stage.ifBlank { "stand still" } else "press & hold 2.5 s",
                    style = MaterialTheme.typography.labelSmall,
                    color = app.textFaint,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun NetworkRow(net: WifiTracker.NetworkEntry) {
    val app = LocalAppColors.current
    val level = SignalMath.strength01(net.rssi.toFloat())
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, app.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    net.ssid,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${WifiTracker.bandLabel(net.band)}" +
                        (if (net.channel > 0) " · ch ${net.channel}" else "") +
                        " · ${Format.rssi(net.rssi)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = app.textFaint
                )
            }
            Spacer(Modifier.width(10.dp))
            LevelBars(level, app)
        }
    }
}

@Composable
private fun LevelBars(level: Float, app: com.findbeam.app.ui.theme.AppColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val thresholds = listOf(0.2f, 0.4f, 0.6f, 0.8f)
        thresholds.forEach { t ->
            val active = level >= t
            val h = 6.dp + (t * 10f).dp
            Box(
                Modifier
                    .width(5.dp)
                    .height(h)
                    .background(
                        when {
                            active && t >= 0.6f -> app.neon
                            active && t >= 0.4f -> Amber
                            active -> app.danger
                            else -> app.border
                        },
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun SaveSpotDialog(
    pending: PendingSave,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val app = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this spot") },
        text = {
            Column {
                Text(
                    "${pending.band} · ${Format.rssi(pending.rssi)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = app.textDim
                )
                Text(
                    "Latency ${Format.latency(pending.latencyMs)} · Speed ${Format.mbps(pending.downMbps)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = app.textDim
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Bedroom corner") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name.ifBlank { "My spot" }) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Discard") }
        }
    )
}
