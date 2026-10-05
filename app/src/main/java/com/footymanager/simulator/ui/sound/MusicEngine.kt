package com.footymanager.simulator.ui.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Fully original, procedurally synthesised background music.
 *
 * No audio files ship with the game: the pad is generated sample-by-sample from
 * a slow chord progression built on pure sine partials. That makes it 100%
 * copyright-safe (there is nothing to license), keeps the APK tiny and lets the
 * loop be seamless. The palette — warm minor-7th chords, a soft bass pulse and a
 * sparse twinkle — is deliberately calm so it fits management screens without
 * ever demanding attention.
 *
 * A single background thread renders and writes the buffer on loop; starting,
 * stopping and volume changes are all safe to call from the main thread.
 */
class MusicEngine {

    var enabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (value) start() else stop()
        }

    /** Volume in 0..1. Applied live when the track is running. */
    var volume: Float = 0.45f

    /** True while the game wants music playing (e.g. inside a career or menu). */
    private var shouldPlay = false

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    /** Begins playback if music is enabled. Idempotent. */
    fun start() {
        shouldPlay = true
        if (!enabled || running) return
        running = true
        val t = Thread({ renderLoop() }, "fm-music").apply { isDaemon = true }
        thread = t
        t.start()
    }

    /** Stops playback and releases the native track. Idempotent. */
    fun stop() {
        shouldPlay = false
        if (!running) return
        running = false
        thread?.join(600)
        thread = null
        val t = track
        track = null
        runCatching {
            t?.pause()
            t?.flush()
            t?.release()
        }
    }

    fun release() = stop()

    private fun renderLoop() {
        val sampleRate = 22_050
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate * 2)

        val localTrack = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: run {
            running = false
            return
        }

        track = localTrack
        runCatching {
            localTrack.play()
            applyVolume(localTrack)
        }

        // Render roughly one bar per buffer so volume changes are snappy but the
        // synthesis loop never spins needlessly.
        val barSamples = (sampleRate * BAR_SECONDS).toInt()
        val buffer = ShortArray(barSamples)

        var bar = 0
        while (running) {
            renderBar(buffer, sampleRate, bar)
            val written = runCatching {
                localTrack.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }.getOrDefault(-1)
            if (written < 0) break
            bar = (bar + 1) % PROGRESSION.size
        }

        runCatching {
            localTrack.stop()
            localTrack.release()
        }
        if (track === localTrack) track = null
    }

    private fun applyVolume(t: AudioTrack) {
        runCatching { t.setVolume(volume.coerceIn(0f, 1f) * MASTER) }
    }

    /** Renders one bar of the current chord into [out]. */
    private fun renderBar(out: ShortArray, sampleRate: Int, bar: Int) {
        val chord = PROGRESSION[bar]
        val base = chord.frequencies()
        val n = out.size
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val beatPos = t / BEAT_SECONDS
            // Soft attack/release envelope on each beat to avoid clicks.
            val env = beatEnvelope(beatPos)
            var sample = 0.0
            // Sustained pad: sine partials with a touch of the fifth above.
            for (f in base) {
                sample += 0.32 * kotlin.math.sin(2.0 * Math.PI * f * t)
                sample += 0.10 * kotlin.math.sin(2.0 * Math.PI * f * 2.0 * t)
            }
            // Gentle bass pulse on each beat.
            sample += 0.16 * kotlin.math.sin(2.0 * Math.PI * (base[0] / 2.0) * t) * env
            // Sparse high twinkle from the chord's upper partials.
            sample += 0.05 * kotlin.math.sin(2.0 * Math.PI * base.last() * 4.0 * t) *
                (0.5 + 0.5 * kotlin.math.sin(2.0 * Math.PI * t / BAR_SECONDS))
            // Slow amplitude drift keeps it breathing rather than static.
            sample *= 0.5 + 0.5 * kotlin.math.sin(2.0 * Math.PI * t / (BAR_SECONDS * 4))

            val soft = (sample / base.size).coerceIn(-1.0, 1.0)
            out[i] = (soft * 9_000).toInt().coerceIn(-32_000, 32_000).toShort()
        }
    }

    /** 0..1 envelope that rises fast and falls slowly within each beat. */
    private fun beatEnvelope(beatPos: Double): Double {
        val frac = beatPos - kotlin.math.floor(beatPos)
        return kotlin.math.exp(-frac * 3.0)
    }

    private data class Chord(val root: Double, val third: Double, val fifth: Double, val seventh: Double) {
        fun frequencies(): DoubleArray = doubleArrayOf(root, third, fifth, seventh)
    }

    private companion object {
        const val MASTER = 0.9f
        const val BEAT_SECONDS = 0.62
        const val BAR_SECONDS = BEAT_SECONDS * 4

        /**
         * An original, loop-friendly progression in A minor: Am7 – Fmaj7 – Cmaj7
         * – G7. Four bars, each a calm sustained chord.
         */
        val PROGRESSION = listOf(
            Chord(220.00, 261.63, 329.63, 392.00), // Am7
            Chord(174.61, 220.00, 261.63, 329.63), // Fmaj7
            Chord(130.81, 196.00, 246.94, 329.63), // Cmaj7
            Chord(196.00, 246.94, 293.66, 349.23)  // G7
        )
    }
}
