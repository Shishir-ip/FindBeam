package com.findbeam.app.core.signal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Exponential moving average for RSSI readings. Kills the jitter so the
 * meter and arrow don't jump around, while staying responsive.
 */
class SignalFilter(private val alpha: Float = 0.25f) {
    private var value: Float = Float.NaN

    fun update(rssi: Int): Float {
        val v = rssi.toFloat()
        value = if (value.isNaN()) v else value + alpha * (v - value)
        return value
    }

    fun current(): Float = if (value.isNaN()) 0f else value

    fun reset() {
        value = Float.NaN
    }
}

/** Shared signal/angle math used across the finder and Wi-Fi screens. */
object SignalMath {

    /** Map RSSI (-100..-35 dBm) to a 0..1 strength. */
    fun strength01(rssi: Float): Float = ((100f + rssi) / 65f).coerceIn(0f, 1f)

    /** Rough distance estimate from RSSI using a log-distance path loss model. */
    fun distanceMeters(rssi: Int, txPower: Float = -59f, n: Float = 2.2f): Float {
        if (rssi <= -95) return -1f
        return 10f.pow((txPower - rssi) / (10f * n))
    }

    /** Shortest signed difference between two angles in degrees (result in -180..180). */
    fun angleDiff(target: Float, from: Float): Float {
        var d = (target - from) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    fun normalizeDeg(deg: Float): Float {
        var d = deg % 360f
        if (d < 0f) d += 360f
        return d
    }

    /** Weighted circular mean of angles in degrees -> 0..360. */
    fun circularMean(anglesDeg: List<Float>, weights: List<Float>): Float {
        var x = 0.0
        var y = 0.0
        var wSum = 0.0
        for (i in anglesDeg.indices) {
            val rad = anglesDeg[i] * PI / 180.0
            val w = weights[i].coerceAtLeast(1e-6f).toDouble()
            x += w * cos(rad)
            y += w * sin(rad)
            wSum += w
        }
        if (wSum <= 0.0) return 0f
        val deg = Math.toDegrees(atan2(y, x))
        return normalizeDeg(deg.toFloat())
    }

    /**
     * Smoothly interpolate between two angles along the shortest arc.
     * t = 0 -> a, t = 1 -> b.
     */
    fun circularLerp(a: Float, b: Float, t: Float): Float {
        val d = angleDiff(b, a)
        return normalizeDeg(a + d * t.coerceIn(0f, 1f))
    }

    /** Small helper for emphasis curves. */
    fun ease(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return 1f - exp(-3f * c)
    }
}
