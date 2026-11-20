package com.findbeam.app.ui.finder

import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findbeam.app.core.haptics.Haptics
import com.findbeam.app.core.signal.ProximityTracker
import com.findbeam.app.core.signal.SignalMath
import com.findbeam.app.core.store.Locator
import com.findbeam.app.ui.components.CompassCalibrationBanner
import com.findbeam.app.ui.components.ConfettiOverlay
import com.findbeam.app.ui.components.GlowArrow
import com.findbeam.app.ui.components.SignalGauge
import com.findbeam.app.ui.theme.LocalAppColors
import com.findbeam.app.util.Format

@Composable
fun TrackingScreen(
    viewModel: FinderViewModel,
    onDone: () -> Unit
) {
    val app = LocalAppColors.current
    val context = LocalContext.current
    val track by viewModel.trackUi.collectAsStateWithLifecycle()

    // Keep the screen awake while guiding (setting-controlled).
    val keepOn = Locator.settingsStore.settings.collectAsState().value.keepScreenOn
    DisposableEffect(keepOn) {
        val window = (context as? android.app.Activity)?.window
        if (keepOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Smooth, unwrapped arrow rotation.
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(track.arrowAngle) {
        val current = rotation.value
        val delta = SignalMath.angleDiff(track.arrowAngle, current)
        rotation.animateTo(
            current + delta,
            spring(dampingRatio = 0.72f, stiffness = 180f)
        )
    }

    // Haptic tick when the arrow crosses into/out of the aligned zone.
    val wasAligned = remember { mutableStateOf(false) }
    LaunchedEffect(track.aligned) {
        if (track.phase == TrackPhase.GUIDING && track.aligned != wasAligned.value) {
            wasAligned.value = track.aligned
            if (Locator.settingsStore.settings.value.haptics) {
                Haptics.tick(context, if (track.aligned) 220 else 90)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                viewModel.cancelTracking()
                onDone()
            }) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column {
                Text(
                    track.deviceName.ifBlank { "Finding…" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    when (track.phase) {
                        TrackPhase.DIRECTION_SCAN -> "Direction scan"
                        TrackPhase.GUIDING -> "Guiding you"
                        TrackPhase.ARRIVED -> "Found"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = app.textFaint
                )
            }
        }

        AnimatedContent(
            targetState = track.phase,
            transitionSpec = {
                (fadeIn(tween(260)) + slideHorizontallyInside())
                    .togetherWith(fadeOut(tween(180)))
            },
            label = "phase",
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp)
        ) { phase ->
            when (phase) {
                TrackPhase.DIRECTION_SCAN -> DirectionScanPhase(track, viewModel)
                TrackPhase.GUIDING -> GuidingPhase(track, rotation.value, viewModel)
                TrackPhase.ARRIVED -> ArrivedPhase(track, viewModel, onDone)
            }
        }
    }
}

private fun AnimatedContentTransitionScope<TrackPhase>.slideHorizontallyInside() =
    slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Up,
        tween(300)
    )

// ------------------------------------------------------------- phase 1

@Composable
private fun DirectionScanPhase(track: TrackState, viewModel: FinderViewModel) {
    val app = LocalAppColors.current
    val animatedCoverage by animateFloatAsState(
        track.spinCoverage, spring(dampingRatio = 0.8f, stiffness = 60f), label = "coverage"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Hold your phone flat at chest height",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Turn around slowly — a full circle — while FindBeam measures the signal from every direction.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = app.textDim
        )

        Spacer(Modifier.height(28.dp))

        // Coverage ring
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(230.dp)) {
            CircularProgressIndicator(
                progress = { animatedCoverage },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
                color = app.neon,
                trackColor = app.border,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = app.neon,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${(animatedCoverage * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "circle covered",
                    style = MaterialTheme.typography.labelSmall,
                    color = app.textFaint
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            StatBadge(
                value = "${track.spinSamples}",
                label = "readings"
            )
            StatBadge(
                value = Format.rssiFloat(track.liveRssi),
                label = "signal now"
            )
            StatBadge(
                value = "${(track.scanConfidence * 100).toInt()}%",
                label = "confidence"
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { viewModel.beginGuiding() },
            enabled = track.readyToGuide,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = app.neon,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                if (track.readyToGuide) "Start guiding" else "Keep turning…",
                style = MaterialTheme.typography.labelLarge
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                viewModel.cancelTracking()
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel", color = app.textDim)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatBadge(value: String, label: String) {
    val app = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = app.textFaint
        )
    }
}

// ------------------------------------------------------------- phase 2

@Composable
private fun GuidingPhase(track: TrackState, rotationDeg: Float, viewModel: FinderViewModel) {
    val app = LocalAppColors.current
    val warmer = track.trend == ProximityTracker.Trend.WARMER
    val colder = track.trend == ProximityTracker.Trend.COLDER

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (track.needsCalibration) {
            CompassCalibrationBanner()
            Spacer(Modifier.height(14.dp))
        }
        if (!com.findbeam.app.core.sensors.CompassEngine.isFlatEnough(track.pitchDeg)) {
            Text(
                "Hold the phone flat for an accurate arrow",
                style = MaterialTheme.typography.labelMedium,
                color = app.amber
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(8.dp))

        GlowArrow(
            angleDeg = rotationDeg,
            aligned = track.aligned,
            arrived = false,
            modifier = Modifier.size(258.dp)
        )

        Spacer(Modifier.height(6.dp))

        // Direction caption
        val diff = track.arrowAngle
        val caption = when {
            kotlin.math.abs(diff) < 12f -> "Straight ahead"
            diff > 0f -> "Turn right · ${kotlin.math.abs(diff).toInt()}°"
            else -> "Turn left · ${kotlin.math.abs(diff).toInt()}°"
        }
        Text(
            caption,
            style = MaterialTheme.typography.titleLarge,
            color = if (track.aligned) app.neon else MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(18.dp))

        // Signal gauge
        SignalGauge(
            progress = track.progress,
            centerTop = Format.rssiFloat(track.liveRssi),
            centerUnit = "smoothed signal",
            caption = null,
            modifier = Modifier.size(190.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Colder / warmer chip
        val (chipColor, chipText) = when {
            track.progress >= 0.85f -> app.neon to "Very close"
            warmer -> app.neon to "Getting warmer"
            colder -> app.danger to "Getting colder"
            else -> app.textDim to "Holding steady"
        }
        Surface(
            shape = CircleShape,
            color = chipColor.copy(alpha = 0.13f),
            border = androidx.compose.foundation.BorderStroke(1.dp, chipColor.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(chipColor, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    chipText,
                    style = MaterialTheme.typography.labelLarge,
                    color = chipColor
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (track.distanceMeters > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Straighten, null,
                        tint = app.textFaint, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "≈ ${Format.distance(track.distanceMeters)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = app.textDim
                    )
                }
            }
            if (track.steps > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.DirectionsWalk, null,
                        tint = app.textFaint, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${track.steps} steps",
                        style = MaterialTheme.typography.titleSmall,
                        color = app.textDim
                    )
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        OutlinedButton(
            onClick = { viewModel.redoDirectionScan() },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Redo direction scan", color = app.textDim)
        }
        Spacer(Modifier.height(28.dp))
    }
}

// ------------------------------------------------------------- phase 3

@Composable
private fun ArrivedPhase(track: TrackState, viewModel: FinderViewModel, onDone: () -> Unit) {
    val app = LocalAppColors.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (Locator.settingsStore.settings.value.haptics) {
            Haptics.arrive(context)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        ConfettiOverlay(visible = true, modifier = Modifier.fillMaxSize())

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(app.neon.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                GlowArrow(
                    angleDeg = 0f,
                    aligned = true,
                    arrived = true,
                    modifier = Modifier.size(92.dp)
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "You're right next to it",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "The signal peaked here and stopped improving — look around your feet and nearby surfaces.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = app.textDim
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "${Format.rssiFloat(track.liveRssi)} peak · ${(track.trackingMs / 1000).coerceIn(0, 9999)}s search",
                style = MaterialTheme.typography.labelMedium,
                color = app.textFaint
            )
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = {
                    viewModel.cancelTracking()
                    onDone()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = app.neon,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text("Done", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.TextButton(onClick = {
                viewModel.redoDirectionScan()
            }) {
                Text("Keep searching", color = app.textDim)
            }
        }
    }
}
