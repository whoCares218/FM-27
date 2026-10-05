package com.footymanager.simulator

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.viewmodel.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the "simulate to date" feature through the ViewModel, the way the UI
 * does: it must advance every competition, update finances and player statistics,
 * and refuse to run while a live match is unfinished.
 */
@RunWith(AndroidJUnit4::class)
class SimulateToDateViewModelTest {

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
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel() = GameViewModel(application, saveRepository, settingsRepository)

    private fun awaitIdle(timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("Timed out waiting for the ViewModel to settle")
    }

    private fun startCareer(viewModel: GameViewModel, clubIndex: Int = 0) {
        viewModel.startNewCareer("Sim Boss", ClubDatabase.buildAll()[clubIndex].id, Difficulty.NORMAL)
        awaitIdle { viewModel.career.value != null }
    }

    /** Runs a simulate-to-date pass to completion (bypassing the reveal pacing). */
    private fun simulateTo(viewModel: GameViewModel, days: Int) {
        val career = viewModel.career.value!!
        val target = career.date.plusDays(days)
        viewModel.startSimulateToDate(target)
        awaitIdle { viewModel.simulateToDate.value != null }
        viewModel.finishSimulateReveal()
        awaitIdle { viewModel.simulateToDate.value?.finished == true }
    }

    @Test
    fun `simulate to date advances the calendar and reports results`() {
        val vm = newViewModel()
        startCareer(vm)
        val before = vm.career.value!!
        simulateTo(vm, days = 120)
        val state = vm.simulateToDate.value!!
        assertTrue("Results should be collected for the animation", state.total > 0)
        assertTrue("The calendar should advance", vm.career.value!!.date.isAfter(before.date))
        assertFalse("It must not overshoot the target", vm.career.value!!.date.isAfter(state.toDate))
    }

    @Test
    fun `simulate to date updates every competition table`() {
        val vm = newViewModel()
        startCareer(vm)
        simulateTo(vm, days = 200)
        val career = vm.career.value!!

        assertTrue(
            "The domestic table should have points",
            career.leagueTable(career.userLeagueId).sumOf { it.points } > 0
        )
        for (competition in listOf(
            CompetitionType.CHAMPIONS_LEAGUE,
            CompetitionType.EUROPA_LEAGUE,
            CompetitionType.CONFERENCE_LEAGUE
        )) {
            val state = career.europeanState(competition)
            assertTrue("$competition should be active", state.active)
            assertTrue(
                "$competition should have played matches",
                state.table.sumOf { it.played } > 0
            )
        }
    }

    @Test
    fun `simulate to date updates finances and player statistics`() {
        val vm = newViewModel()
        startCareer(vm)
        val before = vm.career.value!!
        val balanceBefore = before.userClub.balance
        val appearancesBefore = before.players.sumOf { it.seasonStats.appearances }
        simulateTo(vm, days = 120)
        val after = vm.career.value!!
        assertTrue("Finances must move", after.userClub.balance != balanceBefore)
        assertTrue(
            "Player statistics must accumulate",
            after.players.sumOf { it.seasonStats.appearances } > appearancesBefore
        )
    }

    @Test
    fun `the summary reports the user's record and league movement`() {
        val vm = newViewModel()
        startCareer(vm)
        simulateTo(vm, days = 160)
        val summary = vm.simulateToDate.value!!.summary
        assertNotNull("A summary should be produced", summary)
        assertEquals(
            "The summary must count the user's revealed matches",
            summary!!.userMatches,
            vm.simulateToDate.value!!.total
        )
        assertTrue(
            "The whole world must be simulated, not just the user's matches",
            summary.matchesSimulated >= summary.userMatches
        )
        assertEquals(
            "User results must partition into wins, draws and losses",
            summary.userMatches,
            summary.userWins + summary.userDraws + summary.userLosses
        )
        assertTrue("The user should have played some matches", summary.userMatches > 0)
    }

    @Test
    fun `simulate to date is blocked while a live match is unfinished`() {
        val vm = newViewModel()
        startCareer(vm)
        assertTrue("A match should be preparable", vm.prepareNextMatch())
        vm.startMatch()
        awaitIdle { vm.matchDay.value?.started == true && vm.matchDay.value?.isPlayed == false }

        val target = vm.career.value!!.date.plusDays(30)
        vm.startSimulateToDate(target)
        assertNull("No simulation may start mid-match", vm.simulateToDate.value)
    }

    @Test
    fun `simulate to date rejects a target that is not in the future`() {
        val vm = newViewModel()
        startCareer(vm)
        val today = vm.career.value!!.date
        vm.startSimulateToDate(today)
        assertNull("Today is not a valid target", vm.simulateToDate.value)
    }

    @Test
    fun `simulate to date rejects a target beyond the season horizon`() {
        val vm = newViewModel()
        startCareer(vm)
        val beyond = vm.career.value!!.simulateHorizon().plusDays(30)
        vm.startSimulateToDate(beyond)
        assertNull("A date past the season end is not valid", vm.simulateToDate.value)
    }

    @Test
    fun `the result is persisted so a restart keeps the simulated date`() {
        val vm = newViewModel()
        startCareer(vm)
        simulateTo(vm, days = 120)
        val expected = vm.career.value!!

        val restarted = newViewModel()
        restarted.continueCareer()
        awaitIdle { restarted.career.value != null }
        val reloaded = restarted.career.value!!
        assertEquals("The date must survive a restart", expected.date, reloaded.date)
        assertEquals(
            "The results must survive a restart",
            expected.results.size,
            reloaded.results.size
        )
    }

    @Test
    fun `clearing the simulation state removes it`() {
        val vm = newViewModel()
        startCareer(vm)
        simulateTo(vm, days = 60)
        assertNotNull(vm.simulateToDate.value)
        vm.clearSimulateToDate()
        assertNull(vm.simulateToDate.value)
    }

    @Test
    fun `matches to simulate counts only fixtures up to the target`() {
        val vm = newViewModel()
        startCareer(vm)
        val career: Career = vm.career.value!!
        val target = career.date.plusDays(90)
        val count = vm.matchesToSimulate(target)
        assertTrue("There should be fixtures in the next ninety days", count > 0)
        assertEquals(
            "The count must match the fixtures within the window",
            career.fixtures.count { !it.isPlayed && it.date != null && !it.date.isAfter(target) },
            count
        )
    }

    @Test
    fun `simulate to date stays usable in seasons two and three`() {
        val vm = newViewModel()
        startCareer(vm)

        for (season in 1..3) {
            assertEquals("Season number must advance", season, vm.career.value!!.seasonNumber)

            // The horizon must always be in the future, and the window must be open.
            val horizon = vm.simulateHorizon()
            assertNotNull("Season $season must expose a horizon", horizon)
            assertTrue(
                "Season $season: the horizon must be after today",
                horizon!!.isAfter(vm.career.value!!.date)
            )
            assertTrue(
                "Season $season: tomorrow must be selectable",
                vm.matchesToSimulate(vm.career.value!!.date.plusDays(60)) > 0
            )

            // Simulate a chunk of the season, then roll into the next one.
            simulateTo(vm, days = 60)
            assertTrue(
                "Season $season: the calendar must advance",
                vm.simulateToDate.value?.finished == true
            )
            vm.clearSimulateToDate()

            if (season < 3) {
                vm.startNextSeason()
                awaitIdle { vm.career.value!!.seasonNumber == season + 1 }
                assertTrue(
                    "Season ${season + 1} must begin on its own start date",
                    vm.career.value!!.date.year >= 2026 + season
                )
            }
        }
    }

    @Test
    fun `the revealed results contain only the user's own matches`() {
        val vm = newViewModel()
        startCareer(vm)
        simulateTo(vm, days = 150)
        val state = vm.simulateToDate.value!!
        assertTrue("The user must have played matches", state.results.isNotEmpty())
        assertTrue(
            "Every revealed result must be a user match",
            state.results.all { it.isUserMatch }
        )
        assertTrue(
            "Every revealed result must involve the user's club",
            state.results.all { it.homeClubId == it.userClubId || it.awayClubId == it.userClubId }
        )
    }

    @Test
    fun `progress counts real matchdays and the whole world's fixtures`() {
        val vm = newViewModel()
        startCareer(vm)
        simulateTo(vm, days = 150)
        val state = vm.simulateToDate.value!!
        assertTrue("Matchdays must be counted", state.matchdaysTotal > 0)
        assertEquals("A finished run reaches its matchday total", state.matchdaysTotal, state.matchdaysDone)
        assertTrue(
            "The world's fixtures must exceed the user's own matches",
            state.matchesSimulated > state.results.size
        )
        assertTrue(
            "Progress must not exceed the target fixture count by much",
            state.matchesSimulated <= state.totalMatches + 200
        )
    }

    @Test
    fun `the summary reports the user's domestic league by name, not a hard-coded label`() {
        val vm = newViewModel()
        startCareer(vm, clubIndex = 4)
        simulateTo(vm, days = 150)
        val summary = vm.simulateToDate.value!!.summary!!
        val expected = com.footymanager.simulator.domain.model.League
            .byId(vm.career.value!!.userLeagueId).name
        assertEquals(
            "The summary must name the user's actual domestic league",
            expected,
            summary.domesticLeagueName
        )
    }
}
