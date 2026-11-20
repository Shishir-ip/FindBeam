package com.findbeam.app.util

import java.util.Locale

/** Small formatting helpers shared by the UI. */
object Format {

    fun rssi(rssi: Int): String = "%d dBm".format(Locale.US, rssi)

    fun rssiFloat(rssi: Float): String = "%.0f dBm".format(Locale.US, rssi)

    fun distance(meters: Float): String = when {
        meters < 0 -> "—"
        meters < 1f -> "%.0f cm".format(Locale.US, meters * 100)
        meters < 10f -> "%.1f m".format(Locale.US, meters)
        else -> "%.0f m".format(Locale.US, meters)
    }

    fun mbps(mbps: Float?): String = when {
        mbps == null || mbps < 0 -> "—"
        mbps >= 100f -> "%.0f Mbps".format(Locale.US, mbps)
        mbps >= 10f -> "%.1f Mbps".format(Locale.US, mbps)
        else -> "%.2f Mbps".format(Locale.US, mbps)
    }

    fun latency(ms: Int?): String = if (ms == null || ms < 0) "—" else "%d ms".format(Locale.US, ms)

    fun relativeTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val diff = (nowMs - epochMs).coerceAtLeast(0)
        val min = diff / 60_000
        return when {
            min < 1 -> "just now"
            min < 60 -> "%dm ago".format(Locale.US, min)
            min < 60 * 24 -> "%dh ago".format(Locale.US, min / 60)
            else -> "%dd ago".format(Locale.US, min / (60 * 24))
        }
    }
}
