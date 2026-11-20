package com.findbeam.app.core.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import com.findbeam.app.core.signal.SignalMath

/**
 * Polls the connected network's live signal and exposes a one-shot scan of
 * every network in range.
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

    private val wifiManager =
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val isWifiEnabled: Boolean get() = wifiManager.isWifiEnabled

    @SuppressLint("MissingPermission")
    fun current(): WifiState {
        if (!isWifiEnabled) {
            return WifiState(false, "", -100, 0, 0f, 0, Band.UNKNOWN, 0, 0)
        }
        return try {
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            val rssi = info?.rssi ?: -100
            val ssid = info?.ssid?.removeSurrounding("\"")?.takeIf {
                it.isNotBlank() && it != "<unknown ssid>"
            } ?: ""
            val freq = info?.frequency ?: 0
            WifiState(
                hasWifi = rssi > -120 && info != null,
                ssid = ssid,
                rssi = rssi,
                level = WifiManager.calculateSignalLevel(rssi, 5),
                normalized = SignalMath.strength01(rssi.toFloat()),
                frequencyMHz = freq,
                band = bandFor(freq),
                channel = channelFor(freq),
                linkSpeedMbps = info?.linkSpeed ?: 0
            )
        } catch (_: SecurityException) {
            WifiState(false, "", -100, 0, 0f, 0, Band.UNKNOWN, 0, 0)
        } catch (_: Exception) {
            WifiState(false, "", -100, 0, 0f, 0, Band.UNKNOWN, 0, 0)
        }
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
