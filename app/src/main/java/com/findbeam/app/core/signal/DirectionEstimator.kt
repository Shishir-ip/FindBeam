package com.findbeam.app.core.signal

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Works out which compass direction a BLE signal is coming from by collecting
 * (heading, rssi) pairs while the user turns around, then doing weighted
 * circular statistics on the strongest cluster of readings.
 *
 * The estimator keeps a sliding time window, so during guidance it continuously
 * re-checks the direction as the user walks.
 */
class DirectionEstimator(
    private val windowMs: Long = 12_000L,
    private val maxSamples: Int = 90
) {

    data class Sample(val headingDeg: Float, val rssi: Int, val t: Long)

    data class Estimate(
        val directionDeg: Float,   // absolute compass direction of the target
        val confidence: Float,     // 0..1
        val sampleCount: Int,
        val bestRssi: Int
    )

    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun addSample(headingDeg: Float, rssi: Int, nowMs: Long) {
        if (rssi <= -100) return
        samples.addLast(Sample(SignalMath.normalizeDeg(headingDeg), rssi, nowMs))
        while (samples.size > maxSamples) samples.removeFirst()
    }

    @Synchronized
    fun prune(nowMs: Long) {
        while (samples.isNotEmpty() && nowMs - samples.first().t > windowMs) {
            samples.removeFirst()
        }
    }

    @Synchronized
    fun estimate(nowMs: Long): Estimate? {
        prune(nowMs)
        if (samples.size < 8) return null

        val list = samples.toList()
        val best = list.maxOf { it.rssi }

        // Cluster: readings within 4 dB of the strongest one.
        val cluster = list.filter { it.rssi >= best - 4 }

        // Weight = closeness to the best reading, squared for emphasis.
        val weights = cluster.map { s ->
            val closeness = (s.rssi - (best - 12)).toFloat().coerceAtLeast(0.5f) / 12f
            closeness * closeness
        }
        val angles = cluster.map { it.headingDeg }
        val direction = SignalMath.circularMean(angles, weights)

        // Confidence: how tight and well-populated the strongest cluster is.
        var vecX = 0.0
        var vecY = 0.0
        for (i in angles.indices) {
            val rad = Math.toRadians(angles[i].toDouble())
            vecX += weights[i] * cos(rad)
            vecY += weights[i] * sin(rad)
        }
        val wSum = weights.sum().coerceAtLeast(1e-6f)
        val r = sqrt(vecX * vecX + vecY * vecY) / wSum.toDouble()
        val sizeFactor = (cluster.size / 14f).coerceIn(0f, 1f)
        val confidence = ((r * 0.55 + 0.45) * sizeFactor).toFloat().coerceIn(0f, 1f)

        return Estimate(direction, confidence, cluster.size, best)
    }

    @Synchronized
    fun clear() = samples.clear()

    @Synchronized
    fun sampleCount(): Int = samples.size
}
