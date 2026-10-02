package com.footymanager.simulator.domain.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.footymanager.simulator.domain.model.Career
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fm_saves")

/**
 * Local, offline save storage backed by DataStore.
 *
 * The whole career is serialized to a single JSON string. This keeps saves
 * atomic (no partially written state) and means a corrupted or future-versioned
 * save can be detected and discarded rather than crashing the game.
 *
 * The [DataStore] is injected so tests can supply an isolated store; the
 * convenience constructor uses the app-wide one.
 */
class SaveRepository(private val dataStore: DataStore<Preferences>) : CareerStore {

    constructor(context: Context) : this(context.dataStore)

    private val activeSaveKey = stringPreferencesKey("active_career")

    /**
     * Persists the career, stamping the save time so the UI can show when the
     * game was last written.
     */
    override suspend fun save(career: Career): Boolean = try {
        val toWrite = career.copy(lastSavedEpochMs = System.currentTimeMillis())
        val payload = SaveCodec.encode(toWrite)
        dataStore.edit { prefs -> prefs[activeSaveKey] = payload }
        true
    } catch (t: Throwable) {
        false
    }

    /** Loads the saved career, or null when there is nothing to resume. */
    override suspend fun load(): Career? = try {
        val prefs = dataStore.data.first()
        val payload = prefs[activeSaveKey]
        if (payload == null) null else SaveCodec.decode(payload)
    } catch (t: Throwable) {
        // A save written by an incompatible version is discarded rather than
        // crashing the app on launch.
        null
    }

    override suspend fun hasSave(): Boolean = try {
        val prefs = dataStore.data.first()
        prefs[activeSaveKey] != null
    } catch (t: Throwable) {
        false
    }

    override suspend fun deleteSave() {
        try {
            dataStore.edit { prefs -> prefs.remove(activeSaveKey) }
        } catch (_: Throwable) {
            // Nothing to do: the save is already gone or unreadable.
        }
    }
}
