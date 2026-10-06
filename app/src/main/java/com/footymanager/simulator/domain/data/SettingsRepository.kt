package com.footymanager.simulator.domain.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.ui.sound.MusicPlayMode
import com.footymanager.simulator.ui.sound.MusicTrack
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "fm_settings")

enum class AnimationSpeed(val label: String, val multiplier: Float) {
    OFF("Off", 0f),
    SLOW("Slow", 1.6f),
    NORMAL("Normal", 1.0f),
    FAST("Fast", 0.5f)
}

data class GameSettings(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    val darkTheme: Boolean = true,
    val difficulty: Difficulty = Difficulty.NORMAL,
    /** Background music. Off stops the player cleanly and persists. */
    val musicEnabled: Boolean = true,
    /** The selected background track. */
    val musicTrack: MusicTrack = MusicTrack.CALM_MANAGER,
    /** Selected-track or shuffle. */
    val musicPlayMode: MusicPlayMode = MusicPlayMode.SELECTED,
    /** Sound-effect loudness, 0..1. */
    val soundVolume: Float = 0.7f,
    /** Music loudness, 0..1. */
    val musicVolume: Float = 0.45f
)

/** Player preferences, stored separately from the career save. */
class SettingsRepository(private val dataStore: DataStore<Preferences>) : SettingsStore {

    constructor(context: Context) : this(context.settingsStore)

    private object Keys {
        val sound = booleanPreferencesKey("sound_enabled")
        val vibration = booleanPreferencesKey("vibration_enabled")
        val animation = stringPreferencesKey("animation_speed")
        val darkTheme = booleanPreferencesKey("dark_theme")
        val difficulty = stringPreferencesKey("difficulty")
        val animationFloat = floatPreferencesKey("animation_multiplier")
        val music = booleanPreferencesKey("music_enabled")
        val musicTrack = stringPreferencesKey("music_track")
        val musicPlayMode = stringPreferencesKey("music_play_mode")
        val soundVolume = floatPreferencesKey("sound_volume")
        val musicVolume = floatPreferencesKey("music_volume")
    }

    override val settings: Flow<GameSettings> = dataStore.data.map { prefs ->
        GameSettings(
            soundEnabled = prefs[Keys.sound] ?: true,
            vibrationEnabled = prefs[Keys.vibration] ?: true,
            animationSpeed = prefs[Keys.animation]?.let { name ->
                AnimationSpeed.entries.firstOrNull { it.name == name }
            } ?: AnimationSpeed.NORMAL,
            darkTheme = prefs[Keys.darkTheme] ?: true,
            difficulty = prefs[Keys.difficulty]?.let { name ->
                Difficulty.entries.firstOrNull { it.name == name }
            } ?: Difficulty.NORMAL,
            musicEnabled = prefs[Keys.music] ?: true,
            musicTrack = prefs[Keys.musicTrack]?.let { name ->
                MusicTrack.entries.firstOrNull { it.name == name }
            } ?: MusicTrack.CALM_MANAGER,
            musicPlayMode = prefs[Keys.musicPlayMode]?.let { name ->
                MusicPlayMode.entries.firstOrNull { it.name == name }
            } ?: MusicPlayMode.SELECTED,
            soundVolume = prefs[Keys.soundVolume] ?: 0.7f,
            musicVolume = prefs[Keys.musicVolume] ?: 0.45f
        )
    }

    override suspend fun setSound(enabled: Boolean) = edit { it[Keys.sound] = enabled }

    override suspend fun setVibration(enabled: Boolean) = edit { it[Keys.vibration] = enabled }

    override suspend fun setAnimationSpeed(speed: AnimationSpeed) = edit {
        it[Keys.animation] = speed.name
        it[Keys.animationFloat] = speed.multiplier
    }

    override suspend fun setDarkTheme(enabled: Boolean) = edit { it[Keys.darkTheme] = enabled }

    override suspend fun setDifficulty(difficulty: Difficulty) = edit { it[Keys.difficulty] = difficulty.name }

    override suspend fun setMusic(enabled: Boolean) = edit { it[Keys.music] = enabled }

    override suspend fun setMusicTrack(track: MusicTrack) = edit { it[Keys.musicTrack] = track.name }

    override suspend fun setMusicPlayMode(mode: MusicPlayMode) = edit {
        it[Keys.musicPlayMode] = mode.name
    }

    override suspend fun setSoundVolume(volume: Float) = edit {
        it[Keys.soundVolume] = volume.coerceIn(0f, 1f)
    }

    override suspend fun setMusicVolume(volume: Float) = edit {
        it[Keys.musicVolume] = volume.coerceIn(0f, 1f)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
