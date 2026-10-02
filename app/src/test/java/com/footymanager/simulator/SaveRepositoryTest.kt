package com.footymanager.simulator

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.data.SaveRepository
import com.footymanager.simulator.domain.model.Difficulty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises [SaveRepository] against a real DataStore file.
 *
 * The ViewModel tests use an in-memory store so they can focus on game logic, so
 * this is the test that proves the production persistence path actually writes,
 * reads and clears the save file.
 */
@RunWith(AndroidJUnit4::class)
class SaveRepositoryTest {

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SaveRepository

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dir = File(app.cacheDir, "saverepo-${System.nanoTime()}").apply { mkdirs() }
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "save.preferences_pb") }
        repository = SaveRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun career() = CareerFactory.create(
        CareerFactory.NewCareerRequest("Repo Boss", ClubDatabase.buildAll()[2].id, Difficulty.NORMAL, 4242L)
    )

    @Test
    fun `a career is written, read back and then deleted`() = runBlocking {
        assertFalse("A fresh store has no save", repository.hasSave())
        assertNull("A fresh store loads nothing", repository.load())

        val original = career()
        assertTrue("Save reports success", repository.save(original))

        assertTrue("The save is now visible", repository.hasSave())
        val restored = repository.load()
        assertNotNull("The save decodes", restored)
        assertEquals(original.userClubId, restored!!.userClubId)
        assertEquals(original.players.size, restored.players.size)
        assertEquals(original.table, restored.table)

        repository.deleteSave()
        assertFalse("The save is gone", repository.hasSave())
        assertNull("Nothing loads after a delete", repository.load())
    }

    @Test
    fun `the newest of several writes wins`() = runBlocking {
        val first = career()
        repository.save(first)

        val updated = first.copy(managerName = "New Name", matchdayIndex = 4)
        repository.save(updated)

        val restored = repository.load()
        assertEquals("New Name", restored?.managerName)
        assertEquals(4, restored?.matchdayIndex)
    }

    @Test
    fun `a corrupted save decodes to null instead of throwing`() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("active_career")] = "not json" }
        assertTrue("The key is present", repository.hasSave())
        assertNull("A corrupt payload is discarded", repository.load())
    }
}
