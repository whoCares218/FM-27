package com.footymanager.simulator.ui.sound

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Very small sound layer built on [ToneGenerator].
 *
 * The game ships no audio assets: every cue is a synthesised tone, so the APK
 * stays tiny and there is nothing to license. Only one tone can sound at a time,
 * which is exactly what a menu/feedback blip needs.
 *
 * All calls are safe to make at any time: if the device refuses to create a
 * generator, or the player has muted sound, [play] simply does nothing.
 */
class SoundManager {

    private var toneGenerator: ToneGenerator? = null

    var enabled: Boolean = true

    private fun generator(): ToneGenerator? {
        if (!enabled) return null
        if (toneGenerator == null) {
            toneGenerator = runCatching {
                ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME)
            }.getOrNull()
        }
        return toneGenerator
    }

    private fun tone(type: Int, durationMs: Int) {
        val generator = generator() ?: return
        runCatching { generator.startTone(type, durationMs) }
    }

    /** A short, neutral tap for navigation and buttons. */
    fun click() = tone(ToneGenerator.TONE_PROP_BEEP, 60)

    /** A confirming chime for successful actions. */
    fun success() = tone(ToneGenerator.TONE_PROP_ACK, 140)

    /** A low buzz for a rejected or failed action. */
    fun failure() = tone(ToneGenerator.TONE_PROP_NACK, 160)

    /** A rising tone for a goal. */
    fun goal() = tone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 260)

    /** A referee whistle for kick-off, half time and full time. */
    fun whistle() = tone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 220)

    /** Releases the native resource. Called when the ViewModel is cleared. */
    fun release() {
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    private companion object {
        const val VOLUME = 70
    }
}
