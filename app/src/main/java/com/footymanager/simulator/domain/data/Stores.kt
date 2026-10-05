package com.footymanager.simulator.domain.data

import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Difficulty
import kotlinx.coroutines.flow.Flow

/**
 * Persistence boundary for the career save.
 *
 * The ViewModel depends on this interface rather than the concrete DataStore
 * implementation, which keeps the game logic testable without touching disk and
 * leaves room for a different backend (for example cloud saves) later.
 */
interface CareerStore {
    suspend fun save(career: Career): Boolean
    suspend fun load(): Career?
    suspend fun hasSave(): Boolean
    suspend fun deleteSave()
}

/** Persistence boundary for player preferences. */
interface SettingsStore {
    val settings: Flow<GameSettings>

    suspend fun setSound(enabled: Boolean)
    suspend fun setVibration(enabled: Boolean)
    suspend fun setAnimationSpeed(speed: AnimationSpeed)
    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setDifficulty(difficulty: Difficulty)
    suspend fun setMusic(enabled: Boolean)
    suspend fun setSoundVolume(volume: Float)
    suspend fun setMusicVolume(volume: Float)
}
