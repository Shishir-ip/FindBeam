package com.findbeam.app.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.findbeam.app.core.signal.SignalMath
import kotlin.math.abs

/**
 * Smoothed compass heading from the rotation vector sensor, plus the raw
 * accuracy status used to prompt the figure-8 calibration.
 */
class CompassEngine(context: Context) : SensorEventListener {

    interface Callback {
        fun onHeading(headingDeg: Float, pitchDeg: Float, accuracy: Int)
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var callback: Callback? = null
    private var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

    private var smoothed = Float.NaN
    private var lastPitch = 0f

    val available: Boolean get() = rotationVector != null

    fun start(cb: Callback): Boolean {
        val sensor = rotationVector ?: return false
        callback = cb
        smoothed = Float.NaN
        accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        return sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        callback = null
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotation = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        val remapped = FloatArray(9)
        // Portrait device: screen azimuth follows the device's yaw.
        SensorManager.remapCoordinateSystem(
            rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped
        )
        val orientation = FloatArray(3)
        SensorManager.getOrientation(remapped, orientation)

        val azimuth = SignalMath.normalizeDeg(Math.toDegrees(orientation[0].toDouble()).toFloat())
        val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()

        // Circular EMA so 359->1 deg doesn't swing the long way round.
        smoothed = if (smoothed.isNaN()) {
            azimuth
        } else {
            SignalMath.circularLerp(smoothed, azimuth, 0.18f)
        }
        lastPitch = 0.9f * lastPitch + 0.1f * pitch

        callback?.onHeading(smoothed, lastPitch, accuracy)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR && accuracy != 0) {
            this.accuracy = accuracy
        }
    }

    companion object {
        /** True when the phone is close enough to flat for reliable guidance. */
        fun isFlatEnough(pitchDeg: Float, toleranceDeg: Float = 38f): Boolean =
            abs(pitchDeg) < toleranceDeg
    }
}
