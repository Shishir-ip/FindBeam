package com.findbeam.app.core.signal

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Works out which compass direction a BLE signal is coming from by collecting
 * (heading, rssi) pairs while the user turns around, then doing weighted
 * circular statistics on the strongest cluster of readings.
 *
 * Two instances are used by the view model:
 *  - an "anchor" estimator that accumulates the entire spin scan (no time
 *    window) so slow turns never lose their early samples;
 *  - a sliding-window estimator for live re-checks while walking.
 *
 * If the strongest-cluster has too few readings (noisy RSSI), the estimate
 * falls back to the weighted mean of all samples instead of failing.
 */
class DirectionEstimator(
    private val windowMs: Long,
    private val maxSamples: Int = 120
) {

    data class Sample(val headingDeg: Float, val rssi: Int, val t: Long)

    data class Estimate(
        val directionDeg: Float,   // absolute compass direction of the target
        val confidence: Float,     // 0..1
        val clusterCount: Int,
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
    private fun prune(nowMs: Long) {
        if (windowMs == Long.MAX_VALUE) return
        while (samples.isNotEmpty() && nowMs - samples.first().t > windowMs) {
            samples.removeFirst()
        }
    }

    @Synchronized
    fun estimate(nowMs: Long): Estimate? {
        prune(nowMs)
        if (samples.size < 5) return null

        val list = samples.toList()
        val best = list.maxOf { it.rssi }

        // Cluster: readings within 4 dB of the strongest one. On noisy
        // channels this can be tiny, so fall back to everything.
        var cluster = list.filter { it.rssi >= best - 4 }
        if (cluster.size < 3) cluster = list

        val weights = cluster.map { s ->
            val closeness = (s.rssi - (best - 12)).toFloat().coerceAtLeast(0.5f) / 12f
            closeness * closeness
        }
        val angles = cluster.map { it.headingDeg }
        val direction = SignalMath.circularMean(angles, weights)

        // Confidence: how tight and well-populated the reading set is.
        var vecX = 0.0
        var vecY = 0.0
        for (i in angles.indices) {
            val rad = Math.toRadians(angles[i].toDouble())
            vecX += weights[i] * cos(rad)
            vecY += weights[i] * sin(rad)
        }
        val wSum = weights.sum().coerceAtLeast(1e-6f)
        val r = sqrt(vecX * vecX + vecY * vecY) / wSum.toDouble()
        val sizeFactor = (samples.size / 12f).coerceIn(0f, 1f)
        val confidence = ((r * 0.55 + 0.45) * sizeFactor).toFloat().coerceIn(0f, 1f)

        return Estimate(direction, confidence, cluster.size, best)
    }

    @Synchronized
    fun clear() = samples.clear()

    @Synchronized
    fun totalSamples(): Int = samples.size
}
