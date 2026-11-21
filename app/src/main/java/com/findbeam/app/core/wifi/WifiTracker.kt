package com.findbeam.app.core.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.findbeam.app.core.signal.SignalMath
import java.util.concurrent.atomic.AtomicReference

/**
 * Live Wi-Fi signal source.
 *
 * Primary (API 29+): ConnectivityManager NetworkCallback — onCapabilitiesChanged
 * pushes fresh RSSI every couple of seconds as `signalStrength` (dBm for
 * Wi-Fi), plus a WifiInfo via transportInfo. This replaces the deprecated
 * WifiManager.connectionInfo, which is stale on Android 12+.
 *
 * Fallback (all versions): periodic polling of WifiManager.connectionInfo.
 */
class WifiTracker(private val context: Context) {

    enum class Band { B24, B5, B6, UNKNOWN }

    data class WifiState(
        val hasWifi: Boolean,
        val ssid: String,
        val rssi: Int,
        val level: Int,          // 0..4 Android scale
        val normalized: Float,   // 0..1 meter position
        val frequencyMHz: Int,
        val band: Band,
        val channel: Int,
        val linkSpeedMbps: Int
    )

    data class NetworkEntry(
        val ssid: String,
        val bssid: String,
        val rssi: Int,
        val level: Int,
        val frequencyMHz: Int,
        val band: Band,
        val channel: Int
    )

    private data class CallbackState(
        val atMs: Long,
        val rssi: Int,
        val ssid: String?,
        val frequencyMHz: Int,
        val linkSpeedMbps: Int
    )

    private val wifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callbackState = AtomicReference<CallbackState?>(null)

    val isWifiEnabled: Boolean get() = wifiManager.isWifiEnabled

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (Build.VERSION.SDK_INT < 29) return
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return

            val signal = caps.signalStrength
            val info = caps.transportInfo as? WifiInfo

            val rssi = when {
                signal > -100 -> signal
                info != null && info.rssi > -100 -> info.rssi
                else -> -127
            }
            callbackState.set(
                CallbackState(
                    atMs = System.currentTimeMillis(),
                    rssi = rssi,
                    ssid = info?.ssid?.removeSurrounding("\"")
                        ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" },
                    frequencyMHz = info?.frequency ?: 0,
                    linkSpeedMbps = info?.linkSpeed ?: 0
                )
            )
        }

        override fun onLost(network: Network) {
            callbackState.set(null)
        }
    }

    /** Registers the live-signal callback (API 29+; no-op below). */
    fun start() {
        if (Build.VERSION.SDK_INT < 29) return
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivity.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
        }
    }

    fun stop() {
        try {
            connectivity.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        callbackState.set(null)
    }

    @SuppressLint("MissingPermission")
    fun current(): WifiState {
        if (!isWifiEnabled) {
            return WifiState(false, "", -100, 0, 0f, 0, Band.UNKNOWN, 0, 0)
        }

        // Fallback / SSID source: (deprecated but functional) connectionInfo.
        var pollRssi = -100
        var pollSsid = ""
        var pollFreq = 0
        var pollLink = 0
        var pollOk = false
        try {
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            if (info != null) {
                pollRssi = info.rssi
                pollSsid = info.ssid?.removeSurrounding("\"")?.takeIf {
                    it.isNotBlank() && it != "<unknown ssid>"
                } ?: ""
                pollFreq = info.frequency
                pollLink = info.linkSpeed
                pollOk = pollRssi > -120
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }

        // Prefer the fresh callback value when available and recent.
        val cb = callbackState.get()
        val useCallback = cb != null && System.currentTimeMillis() - cb.atMs < 6_000

        val rssi = when {
            useCallback && cb.rssi > -100 -> cb.rssi
            pollOk -> pollRssi
            cb != null && cb.rssi > -100 -> cb.rssi
            else -> -100
        }
        val hasWifi = pollOk || (cb != null && cb.rssi > -100)
        if (!hasWifi) {
            return WifiState(false, "", -100, 0, 0f, 0, Band.UNKNOWN, 0, 0)
        }

        val ssid = when {
            !useCallback && pollSsid.isNotBlank() -> pollSsid
            useCallback && !cb.ssid.isNullOrBlank() -> cb.ssid
            pollSsid.isNotBlank() -> pollSsid
            else -> cb?.ssid ?: ""
        }
        val freq = when {
            useCallback && cb.frequencyMHz > 0 -> cb.frequencyMHz
            pollFreq > 0 -> pollFreq
            cb != null && cb.frequencyMHz > 0 -> cb.frequencyMHz
            else -> 0
        }
        val link = when {
            useCallback && cb.linkSpeedMbps > 0 -> cb.linkSpeedMbps
            pollLink > 0 -> pollLink
            else -> 0
        }

        return WifiState(
            hasWifi = true,
            ssid = ssid,
            rssi = rssi,
            level = WifiManager.calculateSignalLevel(rssi, 5),
            normalized = SignalMath.strength01(rssi.toFloat()),
            frequencyMHz = freq,
            band = bandFor(freq),
            channel = channelFor(freq),
            linkSpeedMbps = link
        )
    }

    @SuppressLint("MissingPermission")
    fun startScan(): Boolean = try {
        @Suppress("DEPRECATION")
        wifiManager.startScan()
    } catch (_: SecurityException) {
        false
    } catch (_: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun scanResults(): List<NetworkEntry> {
        return try {
            wifiManager.scanResults
                .sortedByDescending { it.level }
                .distinctBy { it.BSSID }
                .take(40)
                .map { it.toEntry() }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun ScanResult.toEntry() = NetworkEntry(
        ssid = SSID?.removeSurrounding("\"")?.takeIf { it.isNotBlank() } ?: "(hidden)",
        bssid = BSSID,
        rssi = level,
        level = WifiManager.calculateSignalLevel(level, 5),
        frequencyMHz = frequency,
        band = bandFor(frequency),
        channel = channelFor(frequency)
    )

    companion object {
        fun bandFor(freqMHz: Int): Band = when {
            freqMHz in 2400..2500 -> Band.B24
            freqMHz in 4900..5900 -> Band.B5
            freqMHz >= 5925 -> Band.B6
            else -> Band.UNKNOWN
        }

        fun channelFor(freqMHz: Int): Int = when {
            freqMHz in 2412..2484 -> (freqMHz - 2412) / 5 + 1
            freqMHz in 5160..5885 -> (freqMHz - 5160) / 5 + 32
            freqMHz >= 5955 -> (freqMHz - 5955) / 5 + 1
            else -> 0
        }

        fun bandLabel(band: Band): String = when (band) {
            Band.B24 -> "2.4 GHz"
            Band.B5 -> "5 GHz"
            Band.B6 -> "6 GHz"
            Band.UNKNOWN -> "—"
        }
    }
}
