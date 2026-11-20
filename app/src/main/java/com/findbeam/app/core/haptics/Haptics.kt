package com.findbeam.app.core.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Central place for haptic feedback so it respects one implementation. */
object Haptics {

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** Short light tick (UI interactions). */
    fun tick(context: Context, amplitude: Int = 120) {
        val v = vibrator(context) ?: return
        try {
            v.vibrate(VibrationEffect.createOneShot(18, amplitude.coerceIn(1, 255)))
        } catch (_: Exception) {
        }
    }

    /** Medium double pulse (direction locked, record broken). */
    fun doubleTick(context: Context) {
        val v = vibrator(context) ?: return
        try {
            v.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 28, 70, 28),
                    intArrayOf(0, 160, 0, 200),
                    -1
                )
            )
        } catch (_: Exception) {
        }
    }

    /** Rising triplet for "arrived". */
    fun arrive(context: Context) {
        val v = vibrator(context) ?: return
        try {
            v.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 40, 60, 40, 60, 120),
                    intArrayOf(0, 140, 0, 180, 0, 255),
                    -1
                )
            )
        } catch (_: Exception) {
        }
    }

    /** Error / dismiss buzz. */
    fun error(context: Context) {
        val v = vibrator(context) ?: return
        try {
            v.vibrate(VibrationEffect.createOneShot(60, 180))
        } catch (_: Exception) {
        }
    }
}
