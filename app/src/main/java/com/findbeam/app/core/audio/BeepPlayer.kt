package com.findbeam.app.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight software tone generator. Plays repeating beeps whose rate and
 * pitch can be tuned live — used for the proximity "radar" beeps and the
 * Wi-Fi Geiger counter. Runs on its own low-priority-audio thread.
 */
class BeepPlayer {

    private val sampleRate = 44_100

    private var track: AudioTrack? = null
    private var worker: Thread? = null

    @Volatile private var running = false
    @Volatile var rateHz: Float = 2f      // beeps per second (0.2..30)
        set(value) { field = value.coerceIn(0.2f, 30f) }
    @Volatile var freqHz: Float = 880f    // beep pitch (200..2200)
        set(value) { field = value.coerceIn(200f, 2200f) }
    @Volatile var gain: Float = 0.30f
        set(value) { field = value.coerceIn(0f, 0.8f) }

    fun start() {
        if (running) return
        running = true
        worker = Thread({ loop() }, "FindBeam-Tone").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    fun update(rate: Float, freq: Float) {
        rateHz = rate
        freqHz = freq
    }

    fun stop() {
        if (!running) return
        running = false
        worker?.join(600)
        worker = null
    }

    private fun loop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        this.track = track
        try {
            track.play()
            while (running) {
                val intervalMs = (1000f / rateHz).coerceAtLeast(25f)
                val beepMs = (intervalMs * 0.6f).coerceIn(12f, 80f)
                writeTone(freqHz, beepMs.toInt(), gain)
                val restMs = (intervalMs - beepMs).toInt().coerceAtLeast(5)
                var slept = 0
                while (running && slept < restMs) {
                    val chunk = min(20, restMs - slept)
                    Thread.sleep(chunk.toLong())
                    slept += chunk
                }
            }
        } catch (_: InterruptedException) {
        } catch (_: IllegalStateException) {
        } finally {
            try {
                track.pause()
                track.flush()
                track.release()
            } catch (_: Exception) {
            }
            this.track = null
        }
    }

    private fun writeTone(freq: Float, ms: Int, gain: Float) {
        val n = (sampleRate * ms) / 1000
        if (n <= 0) return
        val buf = ShortArray(n)
        val fadeIn = (n * 0.08f).toInt().coerceAtLeast(1)
        val fadeOut = (n * 0.20f).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            var env = 1f
            if (i < fadeIn) env = i / fadeIn.toFloat()
            if (i > n - fadeOut) env = min(env, (n - i) / fadeOut.toFloat())
            val sample = sin(2.0 * PI * freq * i / sampleRate) * gain * env
            buf[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        try {
            track?.write(buf, 0, n, AudioTrack.WRITE_BLOCKING)
        } catch (_: IllegalStateException) {
        }
    }
}
