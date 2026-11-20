package com.findbeam.app.ui.finder

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.findbeam.app.core.audio.BeepPlayer
import com.findbeam.app.core.ble.BleScanner
import com.findbeam.app.core.ble.DeviceCategories
import com.findbeam.app.core.ble.DeviceCategory
import com.findbeam.app.core.haptics.Haptics
import com.findbeam.app.core.sensors.CompassEngine
import com.findbeam.app.core.sensors.StepCounter
import com.findbeam.app.core.signal.DirectionEstimator
import com.findbeam.app.core.signal.ProximityTracker
import com.findbeam.app.core.signal.SignalFilter
import com.findbeam.app.core.signal.SignalMath
import com.findbeam.app.core.store.Locator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.floor

enum class TrackPhase { DIRECTION_SCAN, GUIDING, ARRIVED }

data class DeviceUi(
    val address: String,
    val displayName: String,
    val isUnknown: Boolean,
    val rssiSmoothed: Float,
    val rssiRaw: Int,
    val history: List<Int>,
    val category: DeviceCategory,
    val lastSeen: Long
)

data class ScanUiState(
    val scanning: Boolean = false,
    val devices: List<DeviceUi> = emptyList(),
    val bluetoothOn: Boolean = true,
    val locationServicesOn: Boolean = true,
    val hasPermission: Boolean = true
)

data class TrackState(
    val phase: TrackPhase = TrackPhase.DIRECTION_SCAN,
    val deviceName: String = "",
    val deviceAddress: String = "",
    // Direction-scan phase
    val spinCoverage: Float = 0f,
    val spinSamples: Int = 0,
    val readyToGuide: Boolean = false,
    val scanConfidence: Float = 0f,
    // Shared
    val heading: Float = 0f,
    val liveRssi: Float = 0f,
    // Guidance
    val targetDeg: Float = 0f,
    val arrowAngle: Float = 0f,
    val aligned: Boolean = false,
    val confidence: Float = 0f,
    val progress: Float = 0f,
    val trend: ProximityTracker.Trend = ProximityTracker.Trend.STEADY,
    val distanceMeters: Float = -1f,
    val steps: Int = 0,
    val compassAccuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
    val pitchDeg: Float = 0f,
    val needsCalibration: Boolean = false,
    val trackingMs: Long = 0
)

class FinderViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app
    private val scanner = BleScanner(app)
    private val compass = CompassEngine(app)
    private val stepCounter = StepCounter(app)
    private val proximity = ProximityTracker()
    private val estimator = DirectionEstimator()
    private val beeps = BeepPlayer()
    private val scanRssiFilter = SignalFilter(alpha = 0.3f)
    private val settings get() = Locator.settingsStore

    private class DeviceMutable(
        val address: String,
        var rawName: String?,
        var alias: String?,
        val filter: SignalFilter,
        val history: MutableList<Int>,
        var category: DeviceCategory,
        var lastSeen: Long,
        var companyId: Int?,
        var uuids: List<String>
    )

    private val devices = LinkedHashMap<String, DeviceMutable>()

    private var selectedAddress: String? = null
    private var selectedName: String = ""

    @Volatile private var currentHeading = 0f
    @Volatile private var currentPitch = 0f
    @Volatile private var compassAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

    private var trackingStartedAt = 0L
    private val visitedBins = BooleanArray(12)
    private var scanEstimate: DirectionEstimator.Estimate? = null
    private var autoAdvanceJob: Job? = null
    private var trackLoop: Job? = null
    private var lastScanPublish = 0L

    private val _scanUi = MutableStateFlow(ScanUiState())
    val scanUi: StateFlow<ScanUiState> = _scanUi

    private val _trackUi = MutableStateFlow(TrackState())
    val trackUi: StateFlow<TrackState> = _trackUi

    // ---------------------------------------------------------------- scan

    fun refreshSystemState() {
        val lm = ctx.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val locOn = try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            true
        }
        _scanUi.value = _scanUi.value.copy(
            bluetoothOn = scanner.isBluetoothOn,
            hasPermission = scanner.hasPermission,
            locationServicesOn = locOn
        )
    }

    fun startScan() {
        refreshSystemState()
        if (!scanner.hasPermission || !scanner.isBluetoothOn) return
        scanner.lowLatency = false
        val ok = scanner.start(::onBleResult)
        _scanUi.value = _scanUi.value.copy(scanning = ok)
    }

    fun stopScan() {
        scanner.stop()
        _scanUi.value = _scanUi.value.copy(scanning = false)
    }

    fun refreshDevices() {
        publishScanUi(force = true)
    }

    fun renameDevice(address: String, alias: String?) {
        settings.setAlias(address, alias)
        devices[address]?.let { d ->
            d.alias = alias?.trim()?.takeIf { it.isNotEmpty() }
        }
        publishScanUi(force = true)
        if (selectedAddress == address) {
            selectedName = alias?.trim()?.takeIf { it.isNotEmpty() }
                ?: devices[address]?.rawName ?: selectedName
        }
    }

    private fun onBleResult(d: BleScanner.Device) {
        val alias = settings.alias(d.address)
        val existing = devices[d.address]
        val now = d.timestampMs

        val rec = existing ?: DeviceMutable(
            address = d.address,
            rawName = d.name,
            alias = alias,
            filter = SignalFilter(alpha = 0.35f),
            history = mutableListOf(),
            category = DeviceCategories.categorize(d.name, d.companyId, d.serviceUuids),
            lastSeen = now,
            companyId = d.companyId,
            uuids = d.serviceUuids
        ).also {
            devices[d.address] = it
        }

        rec.rawName = d.name ?: rec.rawName
        rec.alias = alias
        if (rec.category == DeviceCategory.OTHER) {
            rec.category = DeviceCategories.categorize(rec.rawName, d.companyId, d.serviceUuids)
        }
        rec.lastSeen = now
        rec.companyId = d.companyId
        rec.uuids = d.serviceUuids

        val smoothed = rec.filter.update(d.rssi)
        rec.history.add(d.rssi)
        while (rec.history.size > 40) rec.history.removeAt(0)

        if (selectedAddress == d.address) {
            handleTrackingSample(d.rssi, now)
        } else {
            publishScanUi()
        }
        // keep reference used so lint is happy about the var
        @Suppress("UNUSED_EXPRESSION")
        smoothed
    }

    private fun publishScanUi(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastScanPublish < 250) return
        lastScanPublish = now

        val list = devices.values
            .filter { now - it.lastSeen < 15_000 && it.rssiRawVisible() }
            .sortedByDescending { it.filter.current() }
            .map {
                DeviceUi(
                    address = it.address,
                    displayName = it.alias ?: it.rawName ?: "Unknown device",
                    isUnknown = it.alias == null && it.rawName == null,
                    rssiSmoothed = it.filter.current(),
                    rssiRaw = it.history.lastOrNull() ?: -100,
                    history = it.history.toList(),
                    category = it.category,
                    lastSeen = it.lastSeen
                )
            }
        _scanUi.value = _scanUi.value.copy(
            devices = list,
            bluetoothOn = scanner.isBluetoothOn,
            hasPermission = scanner.hasPermission
        )
    }

    private fun DeviceMutable.rssiRawVisible(): Boolean =
        (history.lastOrNull() ?: -100) > -99

    // ------------------------------------------------------------ tracking

    fun selectDevice(address: String, name: String) {
        val rec = devices[address]
        selectedAddress = address
        selectedName = rec?.alias ?: rec?.rawName ?: name

        stopScan()
        proximity.reset()
        estimator.clear()
        java.util.Arrays.fill(visitedBins, false)
        scanEstimate = null
        trackingStartedAt = System.currentTimeMillis()

        scanner.lowLatency = true
        scanner.start(::onBleResult, filterAddress = address)

        compass.start(object : CompassEngine.Callback {
            override fun onHeading(headingDeg: Float, pitchDeg: Float, accuracy: Int) {
                currentHeading = headingDeg
                currentPitch = pitchDeg
                compassAccuracy = accuracy
            }
        })

        val motionGranted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
        if (motionGranted && stepCounter.available) {
            stepCounter.start { stepCount ->
                _trackUi.value = _trackUi.value.copy(steps = stepCount)
            }
        }

        _trackUi.value = TrackState(
            phase = TrackPhase.DIRECTION_SCAN,
            deviceName = selectedName,
            deviceAddress = address
        )

        trackLoop?.cancel()
        trackLoop = viewModelScope.launch {
            while (selectedAddress != null) {
                updateTrackState()
                delay(100)
            }
        }
    }

    fun beginGuiding() {
        if (_trackUi.value.phase != TrackPhase.DIRECTION_SCAN) return
        autoAdvanceJob?.cancel()
        scanEstimate = estimator.estimate(System.currentTimeMillis())
        _trackUi.value = _trackUi.value.copy(
            phase = TrackPhase.GUIDING,
            scanConfidence = scanEstimate?.confidence ?: 0f
        )
    }

    fun redoDirectionScan() {
        if (_trackUi.value.phase == TrackPhase.ARRIVED) return
        autoAdvanceJob?.cancel()
        estimator.clear()
        java.util.Arrays.fill(visitedBins, false)
        scanEstimate = null
        proximity.reset()
        _trackUi.value = _trackUi.value.copy(
            phase = TrackPhase.DIRECTION_SCAN,
            spinCoverage = 0f,
            spinSamples = 0,
            readyToGuide = false,
            scanConfidence = 0f
        )
    }

    fun cancelTracking() {
        selectedAddress = null
        autoAdvanceJob?.cancel()
        trackLoop?.cancel()
        trackLoop = null
        scanner.stop()
        compass.stop()
        stepCounter.stop()
        beeps.stop()
        _trackUi.value = TrackState()
    }

    private fun handleTrackingSample(rssi: Int, nowMs: Long) {
        val state = _trackUi.value
        when (state.phase) {
            TrackPhase.DIRECTION_SCAN -> {
                estimator.addSample(currentHeading, rssi, nowMs)
                val bin = floor(SignalMath.normalizeDeg(currentHeading) / 30f)
                    .toInt().coerceIn(0, 11)
                visitedBins[bin] = true
                val coverage = visitedBins.count { it } / 12f
                val est = estimator.estimate(nowMs)
                scanEstimate = est
                val ready = coverage >= 0.7f && (est?.sampleCount ?: 0) >= 14
                _trackUi.value = state.copy(
                    spinCoverage = coverage,
                    spinSamples = estimator.sampleCount(),
                    scanConfidence = est?.confidence ?: 0f,
                    readyToGuide = ready,
                    liveRssi = scanRssiFilter.update(rssi)
                )
                if (ready) scheduleAutoAdvance()
            }
            TrackPhase.GUIDING -> {
                estimator.addSample(currentHeading, rssi, nowMs)
                val prox = proximity.update(rssi, nowMs)
                if (settings.settings.value.sound && !prox.arrived) {
                    beeps.rateHz = 0.6f + 11.5f * prox.progress
                    beeps.freqHz = 550f + 950f * prox.progress
                }
                if (prox.newBest && settings.settings.value.haptics) {
                    Haptics.doubleTick(ctx)
                }
                if (prox.arrived) onArrived()
            }
            TrackPhase.ARRIVED -> Unit
        }
    }

    private fun scheduleAutoAdvance() {
        if (autoAdvanceJob?.isActive == true) return
        autoAdvanceJob = viewModelScope.launch {
            delay(900)
            if (_trackUi.value.phase == TrackPhase.DIRECTION_SCAN && _trackUi.value.readyToGuide) {
                beginGuiding()
            }
        }
    }

    private fun onArrived() {
        beeps.stop()
        scanner.stop()
        if (settings.settings.value.haptics) Haptics.arrive(ctx)
        _trackUi.value = _trackUi.value.copy(phase = TrackPhase.ARRIVED)
    }

    private fun updateTrackState() {
        val s = _trackUi.value
        val now = System.currentTimeMillis()
        when (s.phase) {
            TrackPhase.DIRECTION_SCAN -> {
                _trackUi.value = s.copy(
                    heading = currentHeading,
                    pitchDeg = currentPitch,
                    compassAccuracy = compassAccuracy,
                    needsCalibration = compassAccuracy < SensorManager.SENSOR_STATUS_ACCURACY_LOW,
                    trackingMs = now - trackingStartedAt
                )
            }
            TrackPhase.GUIDING -> {
                val live = estimator.estimate(now)
                val target = blendTargets(scanEstimate, live)
                val arrow = SignalMath.angleDiff(target, currentHeading)
                val prox = proximity.lastState() ?: return
                _trackUi.value = s.copy(
                    heading = currentHeading,
                    pitchDeg = currentPitch,
                    compassAccuracy = compassAccuracy,
                    needsCalibration = compassAccuracy < SensorManager.SENSOR_STATUS_ACCURACY_LOW,
                    targetDeg = target,
                    arrowAngle = arrow,
                    aligned = kotlin.math.abs(arrow) < 30f,
                    confidence = maxOf(live?.confidence ?: 0f, scanEstimate?.confidence ?: 0f),
                    progress = prox.progress,
                    trend = prox.trend,
                    distanceMeters = prox.distanceMeters,
                    liveRssi = prox.smoothed,
                    trackingMs = now - trackingStartedAt
                )
            }
            TrackPhase.ARRIVED -> Unit
        }
    }

    private fun blendTargets(
        scan: DirectionEstimator.Estimate?,
        live: DirectionEstimator.Estimate?
    ): Float {
        if (scan == null && live == null) return currentHeading
        if (scan == null) return live!!.directionDeg
        if (live == null || live.confidence < 0.35f) return scan.directionDeg
        val w = (live.confidence * 0.6f).coerceAtMost(0.45f)
        return SignalMath.circularLerp(scan.directionDeg, live.directionDeg, w)
    }

    override fun onCleared() {
        scanner.stop()
        compass.stop()
        stepCounter.stop()
        beeps.stop()
        super.onCleared()
    }
}
