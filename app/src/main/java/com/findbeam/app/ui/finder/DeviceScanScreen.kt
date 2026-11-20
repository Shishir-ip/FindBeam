package com.findbeam.app.ui.finder

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DevicesOther
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findbeam.app.core.ble.DeviceCategory
import com.findbeam.app.core.haptics.Haptics
import com.findbeam.app.core.signal.SignalMath
import com.findbeam.app.ui.components.EmptyState
import com.findbeam.app.ui.components.LiveDot
import com.findbeam.app.ui.components.Sparkline
import com.findbeam.app.ui.theme.LocalAppColors
import com.findbeam.app.util.Format

@Composable
fun DeviceScanScreen(
    viewModel: FinderViewModel,
    onBack: () -> Unit,
    onSelect: (String, String) -> Unit
) {
    val app = LocalAppColors.current
    val context = LocalContext.current
    val scan by viewModel.scanUi.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<DeviceCategory?>(null) }
    var renameTarget by remember { mutableStateOf<DeviceUi?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshSystemState()
        viewModel.startScan()
    }

    val filtered = remember(scan.devices, query, filter) {
        scan.devices.filter { d ->
            (filter == null || d.category == filter) &&
                (query.isBlank() || d.displayName.contains(query, ignoreCase = true))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
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
                "Object Finder",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                LiveDot(if (scan.scanning) app.neon else app.textFaint)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (scan.scanning) "Scanning" else "Idle",
                    style = MaterialTheme.typography.labelMedium,
                    color = app.textDim
                )
            }
        }

        // ---- Radar sweep header ----
        RadarSweepHeader(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 20.dp),
            scanning = scan.scanning,
            deviceCount = scan.devices.size,
            accent = app.neon
        )

        // ---- Banners ----
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!scan.hasPermission) {
                Banner(Icons.Rounded.BluetoothDisabled, "Bluetooth permission is needed to see nearby devices.")
            } else if (!scan.bluetoothOn) {
                Banner(Icons.Rounded.BluetoothDisabled, "Bluetooth is off. Turn it on to start scanning.")
            }
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S && scan.hasPermission && !scan.locationServicesOn) {
                Banner(Icons.Rounded.LocationOff, "Location services must be on for Bluetooth scanning on this Android version.")
            }
        }

        // ---- Search + filters ----
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search devices", color = app.textFaint) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = app.textFaint) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = app.neon,
                unfocusedBorderColor = app.border,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == null,
                onClick = { filter = null },
                label = { Text("All") },
                colors = chipColors(app)
            )
            FilterChip(
                selected = filter == DeviceCategory.AUDIO,
                onClick = { filter = DeviceCategory.AUDIO },
                label = { Text("Audio") },
                colors = chipColors(app)
            )
            FilterChip(
                selected = filter == DeviceCategory.WEARABLE,
                onClick = { filter = DeviceCategory.WEARABLE },
                label = { Text("Wearables") },
                colors = chipColors(app)
            )
            FilterChip(
                selected = filter == DeviceCategory.TRACKER,
                onClick = { filter = DeviceCategory.TRACKER },
                label = { Text("Trackers") },
                colors = chipColors(app)
            )
            FilterChip(
                selected = filter == DeviceCategory.OTHER,
                onClick = { filter = DeviceCategory.OTHER },
                label = { Text("Other") },
                colors = chipColors(app)
            )
        }

        Spacer(Modifier.height(6.dp))

        // ---- Device list ----
        if (filtered.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.BluetoothDisabled,
                title = if (scan.scanning) "Looking around…" else "Nothing nearby",
                subtitle = if (scan.scanning)
                    "Keep the lost object close enough to be seen — devices appear as their signal is received."
                else "Start scanning to discover nearby Bluetooth devices."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, bottom = 24.dp, top = 4.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.address }) { d ->
                    DeviceRow(
                        device = d,
                        onClick = {
                            Haptics.tick(context, 160)
                            onSelect(d.address, d.displayName)
                        },
                        onRename = { renameTarget = d }
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initial = if (target.isUnknown) "" else target.displayName,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renameDevice(target.address, name.takeIf { it.isNotBlank() })
                renameTarget = null
            },
            onForget = {
                viewModel.renameDevice(target.address, null)
                renameTarget = null
            }
        )
    }
}

@Composable
private fun chipColors(app: com.findbeam.app.ui.theme.AppColors) = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,
    selectedContainerColor = app.neon.copy(alpha = 0.14f),
    labelColor = app.textDim,
    selectedLabelColor = app.neon
)

@Composable
private fun RadarSweepHeader(
    modifier: Modifier,
    scanning: Boolean,
    deviceCount: Int,
    accent: Color
) {
    val app = LocalAppColors.current
    val transition = rememberInfiniteTransition(label = "sweep")
    val angle by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "angle"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f - 4f
            val c = Offset(size.width / 2f, size.height / 2f)
            // rings
            listOf(0.35f, 0.65f, 1f).forEach { f ->
                drawCircle(
                    color = accent.copy(alpha = 0.10f),
                    radius = r * f,
                    center = c,
                    style = Stroke(width = 2f)
                )
            }
            // sweep wedge
            if (scanning) {
                val wedge = Path().apply {
                    moveTo(c.x, c.y)
                    arcTo(
                        rect = Rect(c.x - r, c.y - r, c.x + r, c.y + r),
                        startAngleDegrees = angle,
                        sweepAngleDegrees = 55f,
                        forceMoveTo = false
                    )
                    close()
                }
                drawPath(
                    wedge,
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                        center = c,
                        radius = r
                    )
                )
            }
            drawCircle(color = accent, radius = 8f, center = c)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$deviceCount",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                if (scanning) "devices in range" else "scan paused",
                style = MaterialTheme.typography.labelSmall,
                color = app.textFaint
            )
        }
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String) {
    val app = LocalAppColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = app.danger.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, app.danger.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = app.danger, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = app.danger)
        }
    }
}

@Composable
private fun DeviceRow(
    device: DeviceUi,
    onClick: () -> Unit,
    onRename: () -> Unit
) {
    val app = LocalAppColors.current
    val level = SignalMath.strength01(device.rssiSmoothed)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, app.border),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(app.neon.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(categoryIcon(device.category), null, tint = app.neon, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${categoryLabel(device.category)} · ${Format.rssi(device.rssiRaw)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = app.textFaint
                )
                Spacer(Modifier.height(6.dp))
                // live level bar
                Box(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .height(4.dp)
                        .background(app.border, RoundedCornerShape(4.dp))
                ) {
                    val width = 0.12f + 0.88f * level
                    Box(
                        Modifier
                            .fillMaxWidth(width)
                            .height(4.dp)
                            .background(
                                when {
                                    level > 0.55f -> app.neon
                                    level > 0.3f -> com.findbeam.app.ui.theme.Amber
                                    else -> app.danger
                                },
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Sparkline(
                history = device.history,
                color = app.neon,
                modifier = Modifier.size(width = 56.dp, height = 22.dp)
            )
            IconButton(onClick = onRename, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Rename",
                    tint = app.textFaint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onForget: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this device") },
        text = {
            Column {
                Text(
                    "Give unknown devices a friendly name so they're easy to find next time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalAppColors.current.textDim
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Left earbud") }
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(value) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) {
                    TextButton(onClick = onForget) { Text("Forget") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

fun categoryIcon(category: DeviceCategory): ImageVector = when (category) {
    DeviceCategory.AUDIO -> Icons.Rounded.Headphones
    DeviceCategory.WEARABLE -> Icons.Rounded.Watch
    DeviceCategory.TRACKER -> Icons.Rounded.MyLocation
    DeviceCategory.PHONE -> Icons.Rounded.Smartphone
    DeviceCategory.COMPUTER -> Icons.Rounded.Computer
    DeviceCategory.OTHER -> Icons.Rounded.DevicesOther
}

fun categoryLabel(category: DeviceCategory): String = when (category) {
    DeviceCategory.AUDIO -> "Audio"
    DeviceCategory.WEARABLE -> "Wearable"
    DeviceCategory.TRACKER -> "Tracker"
    DeviceCategory.PHONE -> "Phone"
    DeviceCategory.COMPUTER -> "Computer"
    DeviceCategory.OTHER -> "Device"
}
