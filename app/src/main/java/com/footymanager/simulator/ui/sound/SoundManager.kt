package com.footymanager.simulator.ui.sound

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Tiny sound-effect layer built on [ToneGenerator].
 *
 * The game ships no audio assets: every cue is a synthesised tone, so the APK
 * stays tiny and there is nothing to license. Only one tone sounds at a time,
 * which is exactly what a menu/feedback blip needs. Volume follows the player's
 * sound-volume setting and all calls are safe at any time.
 */
class SoundManager {

    private var toneGenerator: ToneGenerator? = null
    private var lastPlayedAt = 0L

    var enabled: Boolean = true

    /** Sound-effect loudness, 0..1. */
    var volume: Float = 0.7f
        set(value) {
            val coerced = value.coerceIn(0f, 1f)
            if (coerced != field) {
                field = coerced
                // ToneGenerator has no public volume API, so recreate the native
                // generator lazily at the new level the next time a cue plays.
                runCatching { toneGenerator?.release() }
                toneGenerator = null
            }
        }

    private fun generator(): ToneGenerator? {
        if (!enabled) return null
        if (toneGenerator == null) {
            toneGenerator = runCatching {
                ToneGenerator(AudioManager.STREAM_MUSIC, (volume * 100).toInt().coerceIn(0, 100))
            }.getOrNull()
        }
        return toneGenerator
    }

    private fun tone(type: Int, durationMs: Int) {
        if (!enabled) return
        // Debounce: rapid re-taps within 40ms should not stack into a stutter.
        val now = System.currentTimeMillis()
        if (now - lastPlayedAt < 40) return
        lastPlayedAt = now
        val generator = generator() ?: return
        runCatching { generator.startTone(type, durationMs) }
    }

    /** A short, neutral tap for navigation and buttons. */
    fun click() = tone(ToneGenerator.TONE_PROP_BEEP, 55)

    /** A slightly stronger click for confirmations. */
    fun confirm() = tone(ToneGenerator.TONE_PROP_BEEP2, 90)

    /** A gentle downward tap for cancels. */
    fun cancel() = tone(ToneGenerator.TONE_PROP_NACK, 70)

    /** A confirming chime for successful actions. */
    fun success() = tone(ToneGenerator.TONE_PROP_ACK, 140)

    /** A low buzz for a rejected or failed action. */
    fun failure() = tone(ToneGenerator.TONE_PROP_NACK, 170)

    /** A rising tone for a goal. */
    fun goal() = tone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 280)

    /** A referee whistle for kick-off, half time and full time. */
    fun whistle() = tone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 220)

    /** A bright, satisfying chirp for a completed transfer. */
    fun transfer() = tone(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 200)

    /** A short, neutral select sound for dropdowns and pickers. */
    fun select() = tone(ToneGenerator.TONE_PROP_BEEP, 45)

    /** A soft warning tone for risky or costly actions. */
    fun warning() = tone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 150)

    /** Releases the native resource. Called when the ViewModel is cleared. */
    fun release() {
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }
}
