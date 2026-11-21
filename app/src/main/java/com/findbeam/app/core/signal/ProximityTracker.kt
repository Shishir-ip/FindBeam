package com.findbeam.app.core.signal

/**
 * Decides "colder / warmer" and detects arrival: when the smoothed signal
 * reaches its highest point and stops improving, the user is standing right
 * next to the object.
 */
class ProximityTracker {

    enum class Trend { WARMER, COLDER, STEADY }

    data class State(
        val smoothed: Float,
        val delta: Float,          // change vs ~2.5 s ago
        val trend: Trend,
        val best: Float,
        val newBest: Boolean,      // set for one update when a record is broken
        val arrived: Boolean,
        val progress: Float,       // 0..1 meter position
        val distanceMeters: Float  // -1 when unknown
    )

    private val ema = SignalFilter(alpha = 0.30f)
    private val history = ArrayDeque<Pair<Long, Float>>()
    private val recentRaw = ArrayDeque<Int>()

    private var best = Float.NEGATIVE_INFINITY
    private var bestAt = 0L
    private var firstAt = 0L
    private var arrived = false
    private var lastDelta = 0f
    private var last: State? = null

    /** Most recent state (or null before the first update). */
    fun lastState(): State? = last

    fun reset() {
        ema.reset()
        history.clear()
        recentRaw.clear()
        best = Float.NEGATIVE_INFINITY
        bestAt = 0L
        firstAt = 0L
        arrived = false
        lastDelta = 0f
        last = null
    }

    @Synchronized
    fun update(rssi: Int, nowMs: Long): State {
        if (firstAt == 0L) firstAt = nowMs
        val s = ema.update(rssi)

        history.addLast(nowMs to s)
        while (history.size > 40) history.removeFirst()
        recentRaw.addLast(rssi)
        while (recentRaw.size > 5) recentRaw.removeFirst()

        // Value ~2.5 s ago (or the oldest we have) for the trend delta.
        val past = history.firstOrNull { nowMs - it.first <= 2500 } ?: history.first()
        lastDelta = s - past.second

        var newBest = false
        if (s > best + 0.6f) {
            best = s
            bestAt = nowMs
            newBest = true
        }

        val sinceBest = nowMs - bestAt
        val sinceFirst = nowMs - firstAt
        if (!arrived &&
            sinceFirst > 10_000 &&
            sinceBest > 5_000 &&
            s >= best - 3.5f &&
            (s >= -62f || (sinceBest > 8_000 && s >= -75f))
        ) {
            arrived = true
        }

        val trend = when {
            lastDelta > 1.2f -> Trend.WARMER
            lastDelta < -1.2f -> Trend.COLDER
            else -> Trend.STEADY
        }

        // Median of recent raw readings -> distance robust to spikes.
        val medianRaw = recentRaw.sorted()[recentRaw.size / 2]

        val state = State(
            smoothed = s,
            delta = lastDelta,
            trend = if (arrived) Trend.STEADY else trend,
            best = if (best == Float.NEGATIVE_INFINITY) s else best,
            newBest = newBest && !arrived,
            arrived = arrived,
            progress = SignalMath.strength01(s),
            distanceMeters = SignalMath.distanceMeters(medianRaw)
        )
        last = state
        return state
    }
}
