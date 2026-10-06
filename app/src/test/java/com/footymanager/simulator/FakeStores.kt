package com.footymanager.simulator

import com.footymanager.simulator.domain.data.AnimationSpeed
import com.footymanager.simulator.domain.data.CareerStore
import com.footymanager.simulator.domain.data.GameSettings
import com.footymanager.simulator.domain.data.SettingsStore
import com.footymanager.simulator.ui.sound.MusicPlayMode
import com.footymanager.simulator.ui.sound.MusicTrack
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory career storage for ViewModel tests.
 *
 * The real [com.footymanager.simulator.domain.data.SaveRepository] is a thin,
 * well-covered DataStore wrapper; these tests care about game logic and the
 * order writes are applied in, so they run against memory and let the DataStore
 * round-trip be covered directly by `SaveLoadTest`.
 */
class FakeCareerStore : CareerStore {
    private val mutex = Any()
    private var stored: Career? = null

    /** Number of successful writes, useful for asserting persistence happened. */
    var saveCount = 0
        private set

    override suspend fun save(career: Career): Boolean = synchronized(mutex) {
        stored = career
        saveCount++
        true
    }

    override suspend fun load(): Career? = synchronized(mutex) { stored }

    override suspend fun hasSave(): Boolean = synchronized(mutex) { stored != null }

    override suspend fun deleteSave() {
        synchronized(mutex) { stored = null }
    }
}

/** In-memory settings storage for ViewModel tests. */
class FakeSettingsStore : SettingsStore {
    private val state = MutableStateFlow(GameSettings())

    override val settings: Flow<GameSettings> = state.asStateFlow()

    override suspend fun setSound(enabled: Boolean) = state.update { it.copy(soundEnabled = enabled) }

    override suspend fun setVibration(enabled: Boolean) = state.update { it.copy(vibrationEnabled = enabled) }

    override suspend fun setAnimationSpeed(speed: AnimationSpeed) = state.update { it.copy(animationSpeed = speed) }

    override suspend fun setDarkTheme(enabled: Boolean) = state.update { it.copy(darkTheme = enabled) }

    override suspend fun setDifficulty(difficulty: Difficulty) = state.update { it.copy(difficulty = difficulty) }

    override suspend fun setMusic(enabled: Boolean) = state.update { it.copy(musicEnabled = enabled) }

    override suspend fun setMusicTrack(track: MusicTrack) = state.update { it.copy(musicTrack = track) }

    override suspend fun setMusicPlayMode(mode: MusicPlayMode) = state.update { it.copy(musicPlayMode = mode) }

    override suspend fun setSoundVolume(volume: Float) = state.update { it.copy(soundVolume = volume) }

    override suspend fun setMusicVolume(volume: Float) = state.update { it.copy(musicVolume = volume) }
}
