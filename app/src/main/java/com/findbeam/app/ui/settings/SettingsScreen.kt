package com.findbeam.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.findbeam.app.BuildConfig
import com.findbeam.app.core.store.Locator
import com.findbeam.app.core.store.SettingsStore
import com.findbeam.app.core.update.UpdateChecker
import com.findbeam.app.core.update.UpdateChecker.Outcome
import com.findbeam.app.ui.components.FramedCard
import com.findbeam.app.ui.components.SectionLabel
import com.findbeam.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsStore = Locator.settingsStore
    val settings by settingsStore.settings.collectAsState()

    var updateState by remember { mutableStateOf<Outcome?>(null) }
    var checking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
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
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // ---- Appearance ----
            SectionLabel("Appearance")
            Spacer(Modifier.height(10.dp))
            FramedCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        Icons.Rounded.DarkMode, null,
                        tint = app.textDim, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsStore.ThemeMode.entries.forEach { mode ->
                        val selected = settings.themeMode == mode
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = if (selected) app.neon.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (selected) app.neon.copy(alpha = 0.5f) else app.border
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { settingsStore.setTheme(mode) }
                        ) {
                            Text(
                                mode.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) app.neon else app.textDim,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- Feedback ----
            SectionLabel("Feedback & behaviour")
            Spacer(Modifier.height(10.dp))
            FramedCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
                SwitchRow(
                    icon = Icons.Rounded.MusicNote,
                    title = "Sounds",
                    subtitle = "Proximity beeps and the Wi-Fi Geiger tone",
                    checked = settings.sound,
                    onChange = { settingsStore.setSound(it) }
                )
                SwitchRow(
                    icon = Icons.Rounded.Vibration,
                    title = "Haptics",
                    subtitle = "Vibration ticks for direction and records",
                    checked = settings.haptics,
                    onChange = { settingsStore.setHaptics(it) }
                )
                SwitchRow(
                    icon = Icons.Rounded.Screenshot,
                    title = "Keep screen awake",
                    subtitle = "While finding objects or measuring Wi-Fi",
                    checked = settings.keepScreenOn,
                    onChange = { settingsStore.setKeepScreenOn(it) }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ---- Updates ----
            SectionLabel("Updates")
            Spacer(Modifier.height(10.dp))
            FramedCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Check for updates",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            when {
                                checking -> "Contacting GitHub…"
                                updateState is Outcome.Available ->
                                    "Version ${(updateState as Outcome.Available).info.version} is available"
                                updateState is Outcome.UpToDate -> "You're up to date"
                                updateState is Outcome.Offline -> "Couldn't reach GitHub (offline?)"
                                else -> "Current: v${BuildConfig.VERSION_NAME}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                updateState is Outcome.Available -> app.neon
                                updateState is Outcome.Offline -> app.danger
                                else -> app.textFaint
                            }
                        )
                    }
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = app.neon
                        )
                    } else {
                        IconButton(onClick = {
                            checking = true
                            scope.launch {
                                val outcome = UpdateChecker.check(BuildConfig.VERSION_NAME)
                                updateState = outcome
                                checking = false
                            }
                        }) {
                            Icon(
                                Icons.Rounded.Refresh,
                                "Check for updates",
                                tint = app.neon
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- About ----
            SectionLabel("About")
            Spacer(Modifier.height(10.dp))
            FramedCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(app.neon.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .background(app.neon, RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "FindBeam",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "v${BuildConfig.VERSION_NAME} · built with Jetpack Compose",
                            style = MaterialTheme.typography.bodySmall,
                            color = app.textFaint
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/${BuildConfig.UPDATE_REPO}")
                            )
                            context.startActivity(intent)
                        }
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        null, tint = app.textDim, modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "View project on GitHub",
                        style = MaterialTheme.typography.bodyMedium,
                        color = app.textDim
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Lock, null,
                        tint = app.textFaint, modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "No account · No ads · No data collection · Works offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = app.textFaint
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ---- Update dialogs ----
    when (val outcome = updateState) {
        is Outcome.Available -> {
            AlertDialog(
                onDismissRequest = { updateState = null },
                title = { Text("Update available") },
                text = {
                    Column {
                        Text(
                            "FindBeam ${outcome.info.version} is available on GitHub Releases.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = app.textDim
                        )
                        if (outcome.info.notes.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                outcome.info.notes.take(400),
                                style = MaterialTheme.typography.bodySmall,
                                color = app.textFaint,
                                maxLines = 8
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        updateState = null
                        val url = outcome.info.apkUrl ?: outcome.info.pageUrl
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        )
                    }) {
                        Icon(
                            Icons.Rounded.Download, null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Get update")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updateState = null }) { Text("Later") }
                }
            )
        }
        is Outcome.UpToDate -> {
            AlertDialog(
                onDismissRequest = { updateState = null },
                title = { Text("You're up to date") },
                text = {
                    Text(
                        "FindBeam v${BuildConfig.VERSION_NAME} is the latest version.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = app.textDim
                    )
                },
                confirmButton = {
                    TextButton(onClick = { updateState = null }) { Text("OK") }
                }
            )
        }
        else -> Unit
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val app = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = app.textDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = app.textFaint
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = app.neon,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}
