package com.footymanager.simulator.ui.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Fully original, procedurally synthesised background music with five selectable
 * moods.
 *
 * No audio files ship with the game: each track is generated sample-by-sample
 * from its own chord progression, tempo and timbre. That makes the music 100%
 * copyright-safe (there is nothing to license), keeps the APK tiny and lets every
 * loop be seamless. The five tracks are deliberately distinct — calm, energetic,
 * rhythmic, dramatic and uplifting — so the player can pick a mood that matches
 * how they play.
 *
 * A single background thread renders and writes the buffer on loop. Starting,
 * stopping, changing track and volume changes are all safe to call from the main
 * thread. A track change takes effect on the next rendered bar, so switching does
 * not glitch the current loop.
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

    /** The mood currently selected. Changing it takes effect on the next bar. */
    var track: MusicTrack = MusicTrack.CALM_MANAGER

    /** Selected-track or shuffle. */
    var playMode: MusicPlayMode = MusicPlayMode.SELECTED

    /**
     * True while the game wants music playing (e.g. inside a career or menu).
     * Preview playback ignores this so the settings screen can audition a track
     * even when background music is switched off.
     */
    private var shouldPlay = false

    /** True while a settings-screen preview is playing. */
    private var previewing = false

    /** The track actually being rendered; equals [track] except under shuffle. */
    @Volatile
    private var playingTrack: MusicTrack = MusicTrack.CALM_MANAGER

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var trackOut: AudioTrack? = null

    /** Begins playback if music is enabled. Idempotent. */
    fun start() {
        shouldPlay = true
        startThreadIfNeeded()
    }

    /** Stops playback and releases the native track. Idempotent. */
    fun stop() {
        shouldPlay = false
        if (previewing) return
        stopThread()
    }

    /**
     * Plays the current [track] as a short preview from the settings screen. It
     * runs even when background music is off, and stops cleanly afterwards.
     */
    fun preview() {
        previewing = true
        playingTrack = track
        startThreadIfNeeded()
    }

    /** Stops a preview, returning to normal background playback if enabled. */
    fun stopPreview() {
        if (!previewing) return
        previewing = false
        if (!shouldPlay || !enabled) stopThread()
    }

    fun release() {
        shouldPlay = false
        previewing = false
        stopThread()
    }

    private fun startThreadIfNeeded() {
        if (running) return
        if (!previewing && (!enabled || !shouldPlay)) return
        playingTrack = track
        running = true
        val t = Thread({ renderLoop() }, "fm-music").apply { isDaemon = true }
        thread = t
        t.start()
    }

    private fun stopThread() {
        if (!running) return
        running = false
        thread?.join(600)
        thread = null
        val t = trackOut
        trackOut = null
        runCatching {
            t?.pause()
            t?.flush()
            t?.release()
        }
    }

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

        trackOut = localTrack
        runCatching {
            localTrack.play()
            applyVolume(localTrack)
        }

        val buffer = ShortArray((sampleRate * MAX_BAR_SECONDS).toInt())

        var currentTrack = playingTrack
        var bar = 0
        while (running) {
            // A preview always plays the selected track; background playback
            // follows the selection, or shuffles when the player asks for it.
            val desired = if (previewing || playMode == MusicPlayMode.SELECTED) track else playingTrack
            if (desired != currentTrack) {
                currentTrack = desired
                playingTrack = desired
                bar = 0
            }
            val profile = profiles.getValue(currentTrack)
            val barSamples = (sampleRate * profile.barSeconds).toInt().coerceAtMost(buffer.size)
            renderBar(buffer, barSamples, sampleRate, profile, bar)
            val written = runCatching {
                localTrack.write(buffer, 0, barSamples, AudioTrack.WRITE_BLOCKING)
            }.getOrDefault(-1)
            if (written < 0) break
            bar = (bar + 1) % profile.progression.size
            // Shuffle advances to a different mood when the loop comes round, and
            // never immediately repeats the track that just played.
            if (bar == 0 && playMode == MusicPlayMode.SHUFFLE && !previewing) {
                val next = MusicTrack.entries.filter { it != currentTrack }.random()
                playingTrack = next
                currentTrack = next
            }
        }

        runCatching {
            localTrack.stop()
            localTrack.release()
        }
        if (trackOut === localTrack) trackOut = null
    }

    private fun applyVolume(t: AudioTrack) {
        runCatching { t.setVolume(volume.coerceIn(0f, 1f) * MASTER) }
    }

    /**
     * Renders a few bars of a track into a PCM buffer without touching any audio
     * device. Used by tests to prove the five moods really are different, and
     * available for a future waveform preview.
     */
    internal fun renderSamples(track: MusicTrack, bars: Int, sampleRate: Int = 22_050): ShortArray {
        val profile = profiles.getValue(track)
        val barSamples = (sampleRate * profile.barSeconds).toInt()
        val out = ShortArray(barSamples * bars.coerceAtLeast(1))
        val buffer = ShortArray(barSamples)
        for (bar in 0 until bars.coerceAtLeast(1)) {
            renderBar(buffer, barSamples, sampleRate, profile, bar)
            buffer.copyInto(out, destinationOffset = bar * barSamples, startIndex = 0, endIndex = barSamples)
        }
        return out
    }

    /** Renders one bar of the current chord into [out]. */
    private fun renderBar(
        out: ShortArray,
        n: Int,
        sampleRate: Int,
        profile: TrackProfile,
        bar: Int
    ) {
        val chord = profile.progression[bar % profile.progression.size]
        val base = chord.frequencies()
        val beatSeconds = profile.beatSeconds
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val beatPos = t / beatSeconds
            val env = beatEnvelope(beatPos, profile.decay)
            var sample = 0.0
            // Sustained pad: sine partials with a touch of the octave above.
            for (f in base) {
                sample += profile.padGain * kotlin.math.sin(2.0 * Math.PI * f * t)
                sample += profile.padGain * 0.30 * kotlin.math.sin(2.0 * Math.PI * f * 2.0 * t)
            }
            // Bass pulse on each beat.
            sample += profile.bassGain * kotlin.math.sin(2.0 * Math.PI * (base[0] / 2.0) * t) * env
            // Sparse high twinkle from the chord's upper partials.
            sample += profile.twinkleGain * kotlin.math.sin(2.0 * Math.PI * base.last() * 4.0 * t) *
                (0.5 + 0.5 * kotlin.math.sin(2.0 * Math.PI * t / profile.barSeconds))
            // Percussive tick for the rhythmic tracks.
            if (profile.percussive > 0.0) {
                sample += profile.percussive * kotlin.math.sin(2.0 * Math.PI * 180.0 * t) * env * env
            }
            // Slow amplitude drift keeps it breathing rather than static.
            sample *= 0.5 + 0.5 * kotlin.math.sin(2.0 * Math.PI * t / (profile.barSeconds * 4))

            val soft = (sample / base.size).coerceIn(-1.0, 1.0)
            out[i] = (soft * profile.outputScale).toInt().coerceIn(-32_000, 32_000).toShort()
        }
    }

    /** 0..1 envelope that rises fast and falls away within each beat. */
    private fun beatEnvelope(beatPos: Double, decay: Double): Double {
        val frac = beatPos - kotlin.math.floor(beatPos)
        return kotlin.math.exp(-frac * decay)
    }

    private data class Chord(val root: Double, val third: Double, val fifth: Double, val seventh: Double) {
        fun frequencies(): DoubleArray = doubleArrayOf(root, third, fifth, seventh)
    }

    private data class TrackProfile(
        val beatSeconds: Double,
        val decay: Double,
        val padGain: Double,
        val bassGain: Double,
        val twinkleGain: Double,
        val percussive: Double,
        val outputScale: Double,
        val progression: List<Chord>
    ) {
        val barSeconds: Double get() = beatSeconds * 4
    }

    private companion object {
        const val MASTER = 0.9f
        const val MAX_BAR_SECONDS = 4.0

        // A minor 7th, F major 7th, C major 7th, G7 — the calm original pad.
        private val CALM = TrackProfile(
            beatSeconds = 0.62, decay = 3.0, padGain = 0.32, bassGain = 0.16,
            twinkleGain = 0.05, percussive = 0.0, outputScale = 9_000.0,
            progression = listOf(
                Chord(220.00, 261.63, 329.63, 392.00),
                Chord(174.61, 220.00, 261.63, 329.63),
                Chord(130.81, 196.00, 246.94, 329.63),
                Chord(196.00, 246.94, 293.66, 349.23)
            )
        )

        // Driving D minor: quicker beat, stronger pulse, a percussive tick.
        private val MATCHDAY = TrackProfile(
            beatSeconds = 0.42, decay = 5.5, padGain = 0.24, bassGain = 0.24,
            twinkleGain = 0.06, percussive = 0.10, outputScale = 9_500.0,
            progression = listOf(
                Chord(146.83, 174.61, 220.00, 261.63),
                Chord(196.00, 233.08, 293.66, 349.23),
                Chord(220.00, 261.63, 329.63, 392.00),
                Chord(174.61, 220.00, 261.63, 329.63)
            )
        )

        // Cool E minor with a rhythmic sixteenth feel.
        private val MODERN = TrackProfile(
            beatSeconds = 0.50, decay = 6.5, padGain = 0.22, bassGain = 0.22,
            twinkleGain = 0.09, percussive = 0.14, outputScale = 9_200.0,
            progression = listOf(
                Chord(164.81, 196.00, 246.94, 293.66),
                Chord(130.81, 164.81, 196.00, 246.94),
                Chord(196.00, 246.94, 293.66, 392.00),
                Chord(146.83, 185.00, 220.00, 277.18)
            )
        )

        // Slow, wide B minor with a long decay — the continental night.
        private val EUROPEAN = TrackProfile(
            beatSeconds = 0.80, decay = 1.8, padGain = 0.36, bassGain = 0.18,
            twinkleGain = 0.04, percussive = 0.0, outputScale = 9_000.0,
            progression = listOf(
                Chord(246.94, 293.66, 369.99, 440.00),
                Chord(220.00, 277.18, 329.63, 415.30),
                Chord(185.00, 233.08, 277.18, 369.99),
                Chord(164.81, 207.65, 246.94, 329.63)
            )
        )

        // Bright C major, rising, with a confident beat.
        private val VICTORY = TrackProfile(
            beatSeconds = 0.46, decay = 4.0, padGain = 0.28, bassGain = 0.20,
            twinkleGain = 0.10, percussive = 0.08, outputScale = 9_600.0,
            progression = listOf(
                Chord(261.63, 329.63, 392.00, 493.88),
                Chord(349.23, 440.00, 523.25, 659.26),
                Chord(392.00, 493.88, 587.33, 698.46),
                Chord(293.66, 369.99, 440.00, 587.33)
            )
        )

        val profiles: Map<MusicTrack, TrackProfile> = mapOf(
            MusicTrack.CALM_MANAGER to CALM,
            MusicTrack.MATCHDAY_ENERGY to MATCHDAY,
            MusicTrack.MODERN_SPORTS to MODERN,
            MusicTrack.EUROPEAN_NIGHT to EUROPEAN,
            MusicTrack.VICTORY_MOTIVATION to VICTORY
        )
    }
}
