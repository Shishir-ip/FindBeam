package com.findbeam.app.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Counts steps walked since tracking started, using the hardware step counter.
 * Requires the ACTIVITY_RECOGNITION permission; silently unavailable otherwise.
 */
class StepCounter(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var callback: ((Int) -> Unit)? = null
    private var baseline: Float = -1f

    val available: Boolean get() = sensor != null

    fun start(cb: (Int) -> Unit): Boolean {
        val s = sensor ?: return false
        callback = cb
        baseline = -1f
        return sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        callback = null
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val total = event.values.firstOrNull() ?: return
        if (baseline < 0) baseline = total
        val steps = (total - baseline).toInt().coerceAtLeast(0)
        callback?.invoke(steps)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
