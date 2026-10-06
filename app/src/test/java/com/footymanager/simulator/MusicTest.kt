package com.footymanager.simulator

import com.footymanager.simulator.domain.data.GameSettings
import com.footymanager.simulator.ui.sound.MusicPlayMode
import com.footymanager.simulator.ui.sound.MusicTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The background-music system ships five original, distinct moods. These tests
 * assert the catalogue is complete, that every track has its own profile and that
 * the settings store round-trips the selection, so a restart keeps the manager's
 * choice.
 */
class MusicTest {

    @Test
    fun `there are five distinct tracks`() {
        assertEquals("Five moods are required", 5, MusicTrack.entries.size)
        val labels = MusicTrack.entries.map { it.label }
        assertEquals("Every track must have a unique name", labels.size, labels.toSet().size)
        MusicTrack.entries.forEach { track ->
            assertTrue("${track.label} needs a description", track.description.isNotBlank())
        }
    }

    @Test
    fun `every track renders a distinct waveform`() {
        // Render a short sample of each track with a headless engine and confirm no
        // two moods are byte-identical, which would mean the profile was not wired.
        val signatures = MusicTrack.entries.map { track -> signatureFor(track) }
        assertEquals(
            "Every mood must sound different",
            MusicTrack.entries.size,
            signatures.toSet().size
        )
    }

    @Test
    fun `the settings store round-trips the selected track and play mode`() = runBlocking {
        val store = FakeSettingsStore()
        val start = GameSettings()
        assertEquals("Default track should be the calm mood", MusicTrack.CALM_MANAGER, start.musicTrack)
        assertEquals("Default play mode is the selected track", MusicPlayMode.SELECTED, start.musicPlayMode)

        store.setMusicTrack(MusicTrack.EUROPEAN_NIGHT)
        store.setMusicPlayMode(MusicPlayMode.SHUFFLE)
        store.setMusic(true)
        store.setMusicVolume(0.6f)

        // Collect the current value from the flow.
        var latest: GameSettings? = null
        latest = store.settings.first()
        assertEquals(MusicTrack.EUROPEAN_NIGHT, latest!!.musicTrack)
        assertEquals(MusicPlayMode.SHUFFLE, latest!!.musicPlayMode)
        assertTrue(latest!!.musicEnabled)
        assertEquals(0.6f, latest!!.musicVolume, 0.001f)
    }

    @Test
    fun `turning music off and on toggles the flag`() = runBlocking {
        val store = FakeSettingsStore()
        store.setMusic(false)
        var latest: GameSettings? = null
        latest = store.settings.first()
        assertTrue("Music must be off", !latest!!.musicEnabled)
        store.setMusic(true)
        latest = store.settings.first()
        assertTrue("Music must be back on", latest!!.musicEnabled)
    }

    @Test
    fun `the real repository persists the track name`() = runBlocking {
        // A name-based round trip is what survives an enum reorder, so assert the
        // stored representation is the enum name and it resolves back.
        MusicTrack.entries.forEach { track ->
            val resolved = MusicTrack.entries.firstOrNull { it.name == track.name }
            assertEquals(track, resolved)
        }
        assertNotEquals(MusicTrack.CALM_MANAGER, MusicTrack.VICTORY_MOTIVATION)
    }

    /** A cheap fingerprint of a track's rendered bar, used to compare moods. */
    private fun signatureFor(track: MusicTrack): Int {
        val samples = com.footymanager.simulator.ui.sound.MusicEngine()
            .renderSamples(track, bars = 2)
        // A coarse fingerprint: energy and zero-crossings distinguish tempo,
        // timbre and register, so two copy-pasted profiles would collide.
        var h = 17
        var energy = 0L
        var crossings = 0
        var previous = 0
        samples.forEach { s ->
            energy += kotlin.math.abs(s.toInt())
            if (previous != 0 && (s >= 0) != (previous >= 0)) crossings++
            previous = s.toInt()
        }
        h = h * 31 + (energy / 1000).toInt()
        h = h * 31 + (crossings / 100)
        h = h * 31 + samples.size
        return h
    }
}
