package com.findbeam.app.core.wifi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

/**
 * Quick network quality test for a saved spot: median HTTP latency plus a
 * short download throughput measurement. Runs only when the user asks for it.
 */
class NetworkTester {

    data class Result(
        val latencyMs: Int?,
        val downMbps: Float?,
        val ok: Boolean
    )

    suspend fun test(onPhase: (String) -> Unit): Result = withContext(Dispatchers.IO) {
        onPhase("Measuring latency")
        val latency = measureLatency()

        var mbps: Float? = null
        if (latency != null) {
            onPhase("Testing download speed")
            mbps = measureThroughput()
        }

        Result(
            latencyMs = latency,
            downMbps = mbps,
            ok = latency != null
        )
    }

    private fun measureLatency(): Int? {
        val samples = mutableListOf<Int>()
        repeat(4) {
            val ms = pingOnce()
            if (ms != null) samples.add(ms)
        }
        if (samples.isEmpty()) return null
        samples.sort()
        return samples[samples.size / 2]
    }

    private fun pingOnce(): Int? {
        return try {
            val url = URL("https://www.gstatic.com/generate_204")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2200
            conn.readTimeout = 2600
            conn.requestMethod = "GET"
            conn.useCaches = false
            conn.instanceFollowRedirects = false
            val start = System.nanoTime()
            val code = conn.responseCode
            val elapsed = ((System.nanoTime() - start) / 1_000_000).toInt()
            try { conn.inputStream.close() } catch (_: Exception) {}
            conn.disconnect()
            if (code in 200..399) elapsed else null
        } catch (_: Exception) {
            null
        }
    }

    private fun measureThroughput(): Float? {
        return try {
            val url = URL("https://speed.cloudflare.com/__down?bytes=6000000")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 7000
            conn.requestMethod = "GET"
            conn.useCaches = false

            var bytes = 0L
            val start = System.nanoTime()
            val deadline = start + 7_000_000_000L
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (System.nanoTime() < deadline) {
                    val read = input.read(buf)
                    if (read < 0) break
                    bytes += read
                }
            }
            val seconds = (System.nanoTime() - start) / 1_000_000_000.0
            conn.disconnect()
            if (bytes > 200_000 && seconds > 0.05) {
                (bytes * 8 / 1_000_000.0 / seconds).toFloat()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
