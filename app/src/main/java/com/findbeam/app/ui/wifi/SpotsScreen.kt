package com.findbeam.app.ui.wifi

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.findbeam.app.core.store.Locator
import com.findbeam.app.core.store.Spot
import com.findbeam.app.ui.components.EmptyState
import com.findbeam.app.ui.theme.LocalAppColors
import com.findbeam.app.ui.theme.RankBronze
import com.findbeam.app.ui.theme.RankGold
import com.findbeam.app.ui.theme.RankSilver
import com.findbeam.app.util.Format

/** Ranked list of saved Wi-Fi spots, best first. */
@Composable
fun SpotsScreen(onBack: () -> Unit) {
    val app = LocalAppColors.current
    val spots by Locator.spotStore.spots.collectAsState()

    val ranked = remember(spots) {
        spots.sortedWith(
            compareByDescending<Spot> { it.rssi }.thenBy { it.latencyMs ?: Int.MAX_VALUE }
        )
    }
    var deleteTarget by remember { mutableStateOf<Spot?>(null) }
    var renameTarget by remember { mutableStateOf<Spot?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
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
                "Saved spots",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (ranked.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.WifiOff,
                title = "No spots yet",
                subtitle = "Use the Wi-Fi meter and press & hold \"Save this spot\" where the signal is strong."
            )
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ranked, key = { it.id }) { spot ->
                    SpotRow(
                        rank = ranked.indexOf(spot) + 1,
                        spot = spot,
                        onDelete = { deleteTarget = spot },
                        onRename = { renameTarget = spot }
                    )
                }
            }
        }
    }

    deleteTarget?.let { spot ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${spot.name}\"?") },
            text = { Text("This saved spot will be removed permanently.") },
            confirmButton = {
                Button(onClick = {
                    Locator.spotStore.remove(spot.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { spot ->
        var name by remember(spot.id) { mutableStateOf(spot.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename spot") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    Locator.spotStore.rename(spot.id, name)
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SpotRow(
    rank: Int,
    spot: Spot,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    val app = LocalAppColors.current
    val rankColor = when (rank) {
        1 -> RankGold
        2 -> RankSilver
        3 -> RankBronze
        else -> app.textFaint
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (rank == 1) rankColor.copy(alpha = 0.55f) else app.border)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(rankColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$rank",
                    style = MaterialTheme.typography.titleMedium,
                    color = rankColor
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    spot.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        spot.ssid.ifBlank { null },
                        spot.band,
                        Format.rssi(spot.rssi),
                        Format.latency(spot.latencyMs),
                        Format.mbps(spot.downMbps)
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = app.textFaint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onRename, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.Edit, "Rename",
                    tint = app.textFaint, modifier = Modifier.size(16.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Rounded.DeleteOutline, "Delete",
                    tint = app.textFaint, modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
