package com.findbeam.app.core.store

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A Wi-Fi quality measurement saved by the user at a physical place. */
data class Spot(
    val id: Long,
    val name: String,
    val ssid: String,
    val rssi: Int,
    val level: Int,
    val band: String,
    val latencyMs: Int?,
    val downMbps: Float?,
    val createdAt: Long
)

/**
 * Persisted list of saved Wi-Fi spots (JSON in app-private storage).
 * Ranked best -> worst by signal, with latency as a tie-breaker.
 */
class SpotStore(context: Context) {

    private val file = File(context.filesDir, "spots.json")

    private val _spots = MutableStateFlow<List<Spot>>(emptyList())
    val spots: StateFlow<List<Spot>> = _spots

    private var nextId: Long = 1

    init {
        load()
    }

    val ranked: List<Spot>
        get() = _spots.value.sortedWith(
            compareByDescending<Spot> { it.rssi }.thenBy { it.latencyMs ?: Int.MAX_VALUE }
        )

    @Synchronized
    fun add(
        name: String,
        ssid: String,
        rssi: Int,
        level: Int,
        band: String,
        latencyMs: Int?,
        downMbps: Float?
    ): Spot {
        val spot = Spot(
            id = nextId++,
            name = name.ifBlank { "Spot ${nextId - 1}" },
            ssid = ssid,
            rssi = rssi,
            level = level,
            band = band,
            latencyMs = latencyMs,
            downMbps = downMbps,
            createdAt = System.currentTimeMillis()
        )
        _spots.value = _spots.value + spot
        persist()
        return spot
    }

    @Synchronized
    fun remove(id: Long) {
        _spots.value = _spots.value.filterNot { it.id == id }
        persist()
    }

    @Synchronized
    fun rename(id: Long, name: String) {
        _spots.value = _spots.value.map {
            if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it
        }
        persist()
    }

    private fun persist() {
        try {
            val arr = JSONArray()
            for (s in _spots.value) {
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("name", s.name)
                        .put("ssid", s.ssid)
                        .put("rssi", s.rssi)
                        .put("level", s.level)
                        .put("band", s.band)
                        .put("latency", s.latencyMs ?: -1)
                        .put("mbps", s.downMbps ?: -1f)
                        .put("createdAt", s.createdAt)
                )
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            val list = mutableListOf<Spot>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val latency = o.optInt("latency", -1)
                val mbps = o.optDouble("mbps", -1.0)
                list.add(
                    Spot(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        ssid = o.optString("ssid"),
                        rssi = o.getInt("rssi"),
                        level = o.getInt("level"),
                        band = o.optString("band", "—"),
                        latencyMs = latency.takeIf { it >= 0 },
                        downMbps = mbps.toFloat().takeIf { it >= 0 },
                        createdAt = o.getLong("createdAt")
                    )
                )
                nextId = maxOf(nextId, o.getLong("id") + 1)
            }
            _spots.value = list
        } catch (_: Exception) {
        }
    }
}
