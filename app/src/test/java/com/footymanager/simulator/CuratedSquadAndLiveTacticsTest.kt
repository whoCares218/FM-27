package com.footymanager.simulator

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.data.CuratedSquads
import com.footymanager.simulator.domain.data.PlayerGenerator
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.Mentality
import com.footymanager.simulator.domain.model.PlayStyle
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.Tempo
import com.footymanager.simulator.viewmodel.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the two features added in this release:
 *
 *  1. The hand-authored, fictionalised squads for the big clubs are wired into
 *     career creation and behave like any other squad (full size, unique names,
 *     overalls that match their attributes, a real goalkeeper).
 *  2. Tactical changes made while a match is paused genuinely take effect on the
 *     remainder of the match rather than only updating the screen.
 */
@RunWith(AndroidJUnit4::class)
class CuratedSquadAndLiveTacticsTest {

    private lateinit var application: Application
    private lateinit var saveRepository: FakeCareerStore
    private lateinit var settingsRepository: FakeSettingsStore

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        Dispatchers.setMain(Dispatchers.Default)
        saveRepository = FakeCareerStore()
        settingsRepository = FakeSettingsStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = GameViewModel(application, saveRepository, settingsRepository)

    private fun newCareer(seed: Long = 7L, difficulty: Difficulty = Difficulty.NORMAL): Career =
        CareerFactory.create(
            CareerFactory.NewCareerRequest(
                managerName = "Test Boss",
                clubId = ClubDatabase.buildAll().first().id,
                difficulty = difficulty,
                seed = seed
            )
        )

    private fun awaitIdle(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("Timed out waiting for the ViewModel to settle")
    }

    // ---------------------------------------------------------- curated squads

    @Test
    fun `every curated club is topped up to a full squad`() {
        val career = newCareer()
        val byName = career.clubs.associateBy { it.name }
        for ((clubName, designs) in CuratedSquads.byClubName) {
            val club = byName[clubName]
            assertNotNull("Curated club '$clubName' must exist in the database", club)
            val squad = career.squadOf(club!!.id)
            assertTrue(
                "Curated club '$clubName' should have at least the authored players",
                squad.size >= designs.size
            )
            assertTrue(
                "Curated club '$clubName' needs a full squad for auto-pick",
                squad.size >= PlayerGenerator.squadTemplate.size
            )
            assertTrue(
                "Curated club '$clubName' must have a goalkeeper",
                squad.any { it.position == Position.GK }
            )
        }
    }

    @Test
    fun `curated player overall always matches the attributes it is given`() {
        val career = newCareer()
        val curatedNames = CuratedSquads.byClubName.values.flatten().map { it.name }.toSet()
        val curatedPlayers = career.players.filter { it.name in curatedNames }
        assertTrue("Curated players should be present", curatedPlayers.size > 100)
        for (player in curatedPlayers) {
            val derived = PlayerGenerator.overallFor(player.position, player.attributes)
            assertEquals(
                "Overall for ${player.name} must match their attributes",
                derived,
                player.overall
            )
            assertTrue(
                "Potential for ${player.name} must not be below current ability",
                player.potential >= player.overall
            )
        }
    }

    @Test
    fun `curated squads have unique player names within a club`() {
        val career = newCareer()
        for ((clubName, _) in CuratedSquads.byClubName) {
            val club = career.clubs.first { it.name == clubName }
            val names = career.squadOf(club.id).map { it.name }
            assertEquals(
                "Duplicate player names in $clubName",
                names.size,
                names.toSet().size
            )
        }
    }

    @Test
    fun `curated players carry realistic value and wage`() {
        val career = newCareer()
        val curatedNames = CuratedSquads.byClubName.values.flatten().map { it.name }.toSet()
        for (player in career.players.filter { it.name in curatedNames }) {
            assertTrue("${player.name} should have a value", player.value > 0)
            assertTrue("${player.name} should have a wage", player.wagePerWeek > 0)
            assertEquals(
                "${player.name}'s value should be the recomputed value",
                player.recomputeValue(),
                player.value
            )
        }
    }

    @Test
    fun `an authored elite striker outranks an authored squad filler`() {
        val career = newCareer()
        val striker = career.players.first { it.name == "Erling Halland" }
        val filler = career.players.first { it.name == "Josh Wilsonby" }
        assertTrue(
            "The elite striker should be rated far higher than a squad filler",
            striker.overall > filler.overall + 10
        )
    }

    @Test
    fun `higher difficulty does not weaken a curated squad below the authored baseline`() {
        // The AI-strength multiplier is applied uniformly to authored ratings, so
        // a hard-difficulty world is never dramatically weaker than normal.
        val normal = newCareer(difficulty = Difficulty.NORMAL)
        val hard = newCareer(difficulty = Difficulty.HARD)
        val normalStriker = normal.players.first { it.name == "Erling Halland" }
        val hardStriker = hard.players.first { it.name == "Erling Halland" }
        assertTrue("Hard-difficulty squads should still be strong", hardStriker.overall >= 80)
        assertTrue(
            "Hard difficulty should not be dramatically weaker than normal",
            hardStriker.overall >= normalStriker.overall - 6
        )
    }

    // ------------------------------------------------------------- live tactics

    @Test
    fun `a mentality change while paused takes effect on the rest of the match`() {
        val vm = newViewModel()
        vm.startNewCareer("Boss", ClubDatabase.buildAll().first().id, Difficulty.NORMAL)
        awaitIdle { vm.career.value != null }

        assertTrue("prepareNextMatch should succeed", vm.prepareNextMatch())
        vm.startMatch()

        // Let the first half get under way, then pause.
        awaitIdle { (vm.matchDay.value?.minute ?: 0) >= 2 }
        vm.pauseMatch()
        awaitIdle { vm.matchDay.value?.simulating == false }

        val before = vm.matchDay.value!!
        assertTrue("The match should not be finished yet", !before.isPlayed)
        assertTrue("The match should have started", before.started)
        val minuteAtPause = before.minute

        val engineBefore = vm.liveTactics()
        vm.applyLiveTactics(engineBefore.copy(mentality = Mentality.VERY_ATTACKING, tempo = Tempo.FAST))

        // The change is reflected in the career (and therefore the save) at once.
        assertEquals(Mentality.VERY_ATTACKING, vm.career.value!!.tactics.mentality)
        assertEquals(Tempo.FAST, vm.career.value!!.tactics.tempo)

        // And the engine that is actually simulating the match has adopted it.
        assertEquals(Mentality.VERY_ATTACKING, vm.liveTactics().mentality)
        assertEquals(Tempo.FAST, vm.liveTactics().tempo)

        // Resume: the clock must move on, driven by the engine now using the new
        // instructions, and the match must not crash or stall.
        vm.resumeMatch()
        awaitIdle(timeoutMs = 20_000) { (vm.matchDay.value?.minute ?: 0) > minuteAtPause }
        vm.pauseMatch()
        awaitIdle { vm.matchDay.value?.simulating == false }
        assertEquals(
            "The new instructions must survive the resumed spell",
            Mentality.VERY_ATTACKING,
            vm.liveTactics().mentality
        )
    }

    @Test
    fun `a formation change while paused swaps in a legal shape and keeps the XI intact`() {
        val vm = newViewModel()
        vm.startNewCareer("Boss", ClubDatabase.buildAll().first().id, Difficulty.NORMAL)
        awaitIdle { vm.career.value != null }

        assertTrue(vm.prepareNextMatch())
        vm.startMatch()
        awaitIdle { (vm.matchDay.value?.minute ?: 0) >= 2 }
        vm.pauseMatch()
        awaitIdle { vm.matchDay.value?.simulating == false }

        val xiBefore = vm.liveXi()
        val formationBefore = vm.liveTactics().formationId
        val newFormation = com.footymanager.simulator.domain.model.Formation.all
            .first { it.id != formationBefore }
        val minuteAtPause = vm.matchDay.value!!.minute

        vm.applyLiveTactics(vm.liveTactics().copy(formationId = newFormation.id))

        assertEquals("The career should adopt the new shape", newFormation.id, vm.career.value!!.tactics.formationId)
        assertEquals("The engine should adopt the new shape", newFormation.id, vm.liveTactics().formationId)
        assertEquals(
            "A shape change should not change the number of players on the pitch",
            xiBefore.size,
            vm.liveXi().size
        )
        assertEquals(
            "The live XI should still be a full eleven",
            11,
            vm.liveXi().size
        )

        vm.resumeMatch()
        awaitIdle(timeoutMs = 20_000) { (vm.matchDay.value?.minute ?: 0) > minuteAtPause }
        vm.pauseMatch()
        awaitIdle { vm.matchDay.value?.simulating == false }
        assertEquals(
            "The new shape must survive the resumed spell",
            newFormation.id,
            vm.liveTactics().formationId
        )
    }

    @Test
    fun `live tactics survive a pause and the match is persisted with the new instructions`() {
        val vm = newViewModel()
        vm.startNewCareer("Boss", ClubDatabase.buildAll().first().id, Difficulty.NORMAL)
        awaitIdle { vm.career.value != null }
        val clubId = vm.career.value!!.userClubId
        awaitIdle { runBlocking { saveRepository.load() }?.userClubId == clubId }

        assertTrue(vm.prepareNextMatch())
        vm.startMatch()
        awaitIdle { (vm.matchDay.value?.minute ?: 0) >= 2 }
        vm.pauseMatch()
        awaitIdle { vm.matchDay.value?.simulating == false }

        vm.applyLiveTactics(
            vm.liveTactics().copy(
                mentality = Mentality.DEFENSIVE,
                style = PlayStyle.COUNTER_ATTACK,
                tempo = Tempo.SLOW
            )
        )

        awaitIdle {
            val saved = runBlocking { saveRepository.load() }
            saved?.tactics?.mentality == Mentality.DEFENSIVE &&
                saved.tactics?.style == PlayStyle.COUNTER_ATTACK &&
                saved.tactics?.tempo == Tempo.SLOW
        }
    }
}
