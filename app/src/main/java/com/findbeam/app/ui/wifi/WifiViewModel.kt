package com.findbeam.app.ui.wifi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.findbeam.app.core.audio.BeepPlayer
import com.findbeam.app.core.haptics.Haptics
import com.findbeam.app.core.store.Locator
import com.findbeam.app.core.wifi.NetworkTester
import com.findbeam.app.core.wifi.WifiTracker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PendingSave(
    val ssid: String,
    val rssi: Int,
    val level: Int,
    val band: String,
    val latencyMs: Int?,
    val downMbps: Float?
)

data class WifiUi(
    val state: WifiTracker.WifiState? = null,
    val wifiEnabled: Boolean = true,
    val bestRssi: Int = -100,
    val newBestPulse: Boolean = false,
    val soundOn: Boolean = true,
    val saving: Boolean = false,
    val saveProgress: Float = 0f,
    val saveStage: String = "",
    val pendingSave: PendingSave? = null,
    val networks: List<WifiTracker.NetworkEntry> = emptyList(),
    val networksScanning: Boolean = false
)

class WifiViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app
    private val tracker = WifiTracker(app)
    private val tester = NetworkTester()
    private val beeps = BeepPlayer()
    private val settings get() = Locator.settingsStore
    private val spotStore get() = Locator.spotStore

    private var pollJob: Job? = null
    private var networksJob: Job? = null
    private var bestRssi = -100
    private var lastBestPulseAt = 0L

    private val _ui = MutableStateFlow(WifiUi(soundOn = settings.settings.value.sound))
    val ui: StateFlow<WifiUi> = _ui

    fun start() {
        stopPolling()
        pollJob = viewModelScope.launch {
            while (isActive) {
                val state = tracker.current()
                val prevBest = bestRssi
                if (state.hasWifi && state.rssi > bestRssi + 1) {
                    bestRssi = state.rssi
                    val now = System.currentTimeMillis()
                    if (now - lastBestPulseAt > 1200) {
                        lastBestPulseAt = now
                        if (prevBest > -100 && settings.settings.value.haptics) {
                            Haptics.doubleTick(ctx)
                        }
                        _ui.value = _ui.value.copy(
                            bestRssi = bestRssi,
                            newBestPulse = true
                        )
                        delay(1400)
                        _ui.value = _ui.value.copy(newBestPulse = false)
                    }
                }
                if (settings.settings.value.sound && state.hasWifi) {
                    beeps.rateHz = 0.8f + 13f * state.normalized
                    beeps.freqHz = 480f + 1000f * state.normalized
                    beeps.start()
                } else {
                    beeps.stop()
                }
                _ui.value = _ui.value.copy(
                    state = state,
                    wifiEnabled = tracker.isWifiEnabled,
                    soundOn = settings.settings.value.sound
                )
                delay(450)
            }
        }
    }

    fun stop() {
        stopPolling()
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        beeps.stop()
    }

    fun toggleSound() {
        val next = !settings.settings.value.sound
        settings.setSound(next)
        _ui.value = _ui.value.copy(soundOn = next)
        if (!next) beeps.stop()
    }

    // ---------------------------------------------------------- save a spot

    /** Hold-to-save: 2.5 s averaging, then the quick latency + speed test. */
    fun beginSaveSpot() {
        if (_ui.value.saving || _ui.value.pendingSave != null) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(saving = true, saveProgress = 0f, saveStage = "Holding steady")

            val samples = mutableListOf<Int>()
            val startAt = System.currentTimeMillis()
            val durationMs = 2500L
            var ssid = ""
            var level = 0
            var band = "—"

            while (true) {
                val elapsed = System.currentTimeMillis() - startAt
                val state = tracker.current()
                if (elapsed >= durationMs) {
                    if (state.hasWifi) {
                        samples.add(state.rssi)
                        ssid = state.ssid
                        level = state.level
                        band = WifiTracker.bandLabel(state.band)
                    }
                    break
                }
                if (state.hasWifi) {
                    samples.add(state.rssi)
                    ssid = state.ssid
                    level = state.level
                    band = WifiTracker.bandLabel(state.band)
                }
                _ui.value = _ui.value.copy(
                    saveProgress = elapsed / durationMs.toFloat()
                )
                delay(180)
            }

            if (samples.isEmpty()) {
                _ui.value = _ui.value.copy(
                    saving = false,
                    saveStage = "",
                    saveProgress = 0f
                )
                return@launch
            }

            val avg = samples.sorted()[samples.size / 2]

            val result = tester.test { phase ->
                _ui.value = _ui.value.copy(saveStage = phase)
            }

            if (settings.settings.value.haptics && result.ok) Haptics.tick(ctx, 200)

            _ui.value = _ui.value.copy(
                saving = false,
                saveProgress = 1f,
                saveStage = "",
                pendingSave = PendingSave(
                    ssid = ssid,
                    rssi = avg,
                    level = level,
                    band = band,
                    latencyMs = result.latencyMs,
                    downMbps = result.downMbps
                )
            )
        }
    }

    fun confirmSave(name: String) {
        val p = _ui.value.pendingSave ?: return
        spotStore.add(
            name = name,
            ssid = p.ssid,
            rssi = p.rssi,
            level = p.level,
            band = p.band,
            latencyMs = p.latencyMs,
            downMbps = p.downMbps
        )
        _ui.value = _ui.value.copy(pendingSave = null)
    }

    fun dismissSave() {
        _ui.value = _ui.value.copy(pendingSave = null)
    }

    // ----------------------------------------------------- nearby networks

    fun refreshNetworks() {
        if (networksJob?.isActive == true) return
        networksJob = viewModelScope.launch {
            _ui.value = _ui.value.copy(networksScanning = true)
            tracker.startScan()
            delay(1600)
            var results = tracker.scanResults()
            if (results.isEmpty()) {
                delay(900)
                results = tracker.scanResults()
            }
            _ui.value = _ui.value.copy(
                networks = results,
                networksScanning = false
            )
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }
}
