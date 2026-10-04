package com.footymanager.simulator

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.viewmodel.GameViewModel
import com.footymanager.simulator.viewmodel.MatchMode
import kotlinx.coroutines.Dispatchers
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
 * Regression tests for the match lifecycle bugs reported after v1.3.0, plus the
 * new pause/resume guarantees that fix them.
 *
 * The live engine is driven through Quick Sim (the same code path Play Match
 * uses, only with larger slices), and a fresh ViewModel is created to mimic the
 * process being killed, so the real persistence round-trip is exercised.
 */
@RunWith(AndroidJUnit4::class)
class MatchLifecycleRegressionTest {

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

    private fun awaitIdle(timeoutMs: Long = 20_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("Timed out waiting for the ViewModel to settle")
    }

    private fun startCareer(viewModel: GameViewModel, clubIndex: Int = 0) {
        viewModel.startNewCareer("Test Boss", ClubDatabase.buildAll()[clubIndex].id, Difficulty.NORMAL)
        awaitIdle { viewModel.career.value != null }
    }

    private fun savedOnDisk(): Career? = kotlinx.coroutines.runBlocking { saveRepository.load() }

    /** Drives one match through Quick Sim, honouring the half-time break. */
    private fun quickSimOneMatch(vm: GameViewModel) {
        assertTrue("prepareNextMatch must succeed", vm.prepareNextMatch())
        vm.startMatch(MatchMode.QUICK)
        awaitIdle { vm.matchDay.value?.awaitingHalfTime == true || vm.matchDay.value?.isPlayed == true }
        if (vm.matchDay.value?.awaitingHalfTime == true) vm.continueSecondHalf()
        awaitIdle { vm.matchDay.value?.isPlayed == true }
        vm.advanceAfterMatch()
        awaitIdle { vm.matchDay.value == null && !vm.isBusy.value }
    }

    @Test
    fun `twelve consecutive matches all resolve and advance the calendar`() {
        val vm = newViewModel()
        startCareer(vm)

        repeat(12) { i ->
            val before = vm.career.value!!
            assertTrue("prepareNextMatch must succeed for match ${i + 1}", vm.prepareNextMatch())
            vm.startMatch(MatchMode.QUICK)
            awaitIdle {
                vm.matchDay.value?.awaitingHalfTime == true || vm.matchDay.value?.isPlayed == true
            }
            if (vm.matchDay.value?.awaitingHalfTime == true) vm.continueSecondHalf()
            awaitIdle { vm.matchDay.value?.isPlayed == true }
            assertNotNull("Match ${i + 1} must produce a result", vm.matchDay.value?.result)
            vm.advanceAfterMatch()
            awaitIdle { vm.matchDay.value == null && !vm.isBusy.value }
            assertFalse("isBusy must be released after match ${i + 1}", vm.isBusy.value)
            assertTrue(
                "The calendar must advance after match ${i + 1}",
                vm.career.value!!.matchdayIndex > before.matchdayIndex
            )
        }
    }

    @Test
    fun `leaving a live match stores a resumable snapshot`() {
        val vm = newViewModel()
        startCareer(vm)
        assertTrue(vm.prepareNextMatch())
        vm.startMatch(MatchMode.QUICK)
        // Let a little of the first half play so there is real state to save.
        Thread.sleep(200)
        vm.leaveMatch()
        assertNull("The match screen must close", vm.matchDay.value)

        awaitIdle { savedOnDisk()?.inProgressMatch != null }
        val snap = savedOnDisk()!!.inProgressMatch!!
        assertFalse("The snapshot must not be marked finished", snap.isFinished)
        assertNotNull("The snapshot must be for the next fixture", snap.matchId)

        // Restarting the process must resume the same match rather than restart it.
        val restarted = newViewModel()
        restarted.continueCareer()
        awaitIdle { restarted.career.value != null }
        assertTrue("A pending match must be reported", restarted.hasPendingMatch)
        assertTrue("prepareNextMatch must rebuild the live match", restarted.prepareNextMatch())
        val resumed = restarted.matchDay.value
        assertNotNull(resumed)
        assertTrue("The resumed match must already be started", resumed!!.started)
        assertEquals("The resumed match must be the same fixture", snap.matchId, resumed.match.id)
    }

    @Test
    fun `resuming a paused match continues from the saved minute`() {
        val vm = newViewModel()
        startCareer(vm)
        assertTrue(vm.prepareNextMatch())
        val fixtureId = vm.matchDay.value!!.match.id
        vm.startMatch(MatchMode.QUICK)
        Thread.sleep(200)
        vm.pauseMatch()
        val pausedMinute = vm.matchDay.value?.minute ?: 0

        val restarted = newViewModel()
        restarted.continueCareer()
        awaitIdle { restarted.career.value != null }
        assertTrue(restarted.prepareNextMatch())
        val resumedMinute = restarted.matchDay.value?.minute ?: -1
        assertEquals("The resumed fixture must match", fixtureId, restarted.matchDay.value!!.match.id)
        assertEquals(
            "The resumed clock must not restart from zero",
            pausedMinute, resumedMinute
        )
        // The resume must be able to finish without error.
        restarted.resumeMatch()
        awaitIdle { restarted.matchDay.value?.awaitingHalfTime == true || restarted.matchDay.value?.isPlayed == true }
        if (restarted.matchDay.value?.awaitingHalfTime == true) restarted.continueSecondHalf()
        awaitIdle { restarted.matchDay.value?.isPlayed == true }
    }

    @Test
    fun `a completed match does not leave a pending snapshot`() {
        val vm = newViewModel()
        startCareer(vm)
        quickSimOneMatch(vm)
        awaitIdle { savedOnDisk()?.inProgressMatch == null }
        assertNull(
            "A finished match must not be replayable",
            savedOnDisk()?.inProgressMatch
        )
    }

    @Test
    fun `restarting mid-season keeps league standings and results`() {
        val vm = newViewModel()
        startCareer(vm)
        repeat(3) { quickSimOneMatch(vm) }
        val onDisk = savedOnDisk()!!
        assertTrue("Three results should be stored", onDisk.results.size >= 3)
        assertEquals("The calendar should be three matchdays on", 3, onDisk.matchdayIndex)

        val restarted = newViewModel()
        restarted.continueCareer()
        awaitIdle { restarted.career.value != null }
        assertEquals(3, restarted.career.value!!.matchdayIndex)
        assertEquals(onDisk.results.size, restarted.career.value!!.results.size)
    }

    /**
     * Play Match, pause in the first half, switch to Quick Sim From Here and pin
     * that the match continues from the same minute with the same score rather
     * than being restarted or regenerated.
     */
    @Test
    fun `quick sim from here continues the same paused match`() {
        val vm = newViewModel()
        startCareer(vm)
        assertTrue(vm.prepareNextMatch())
        val fixtureId = vm.matchDay.value!!.match.id

        vm.startMatch(MatchMode.PLAY)
        awaitIdle { (vm.matchDay.value?.minute ?: 0) >= 3 }
        vm.pauseMatch()
        awaitIdle { vm.matchDay.value?.simulating == false }

        val minuteAtPause = vm.matchDay.value!!.minute
        val homeAtPause = vm.matchDay.value!!.homeGoals
        val awayAtPause = vm.matchDay.value!!.awayGoals
        val feedAtPause = vm.matchDay.value!!.feed.size

        vm.quickSimFromHere()
        awaitIdle { vm.matchDay.value?.awaitingHalfTime == true || vm.matchDay.value?.isPlayed == true }

        val after = vm.matchDay.value!!
        assertEquals("The fixture must not change", fixtureId, vm.matchDay.value!!.match.id)
        assertTrue(
            "Quick Sim From Here must not rewind the clock",
            after.minute >= minuteAtPause
        )
        assertTrue(
            "Quick Sim From Here must not wipe the event feed",
            after.feed.size >= feedAtPause
        )
        assertTrue("The score must never go backwards", after.homeGoals >= homeAtPause)
        assertTrue("The score must never go backwards", after.awayGoals >= awayAtPause)

        if (after.awaitingHalfTime) vm.continueSecondHalf()
        awaitIdle { vm.matchDay.value?.isPlayed == true }
        assertEquals(fixtureId, vm.matchDay.value!!.match.id)
    }

    /**
     * A substitution made while paused must actually change the on-pitch XI and
     * keep the engine consistent, then survive a resume.
     */
    @Test
    fun `a substitution while paused changes the XI and the match carries on`() {
        val vm = newViewModel()
        startCareer(vm)
        assertTrue(vm.prepareNextMatch())
        vm.startMatch(MatchMode.PLAY)
        awaitIdle { (vm.matchDay.value?.minute ?: 0) >= 2 }
        vm.pauseMatch()
        awaitIdle { vm.matchDay.value?.simulating == false }

        val xiBefore = vm.liveXi()
        val off = xiBefore.first()
        val on = vm.liveBench().first()
        vm.makeLiveSubstitution(off, on)

        val xiAfter = vm.liveXi()
        assertFalse("The outgoing player must leave the pitch", off in xiAfter)
        assertTrue("The incoming player must be on the pitch", on in xiAfter)
        assertEquals("A substitution must not change the XI count", xiBefore.size, xiAfter.size)

        // Resume briefly: the clock must move on and the substitute must stay on.
        val minuteAtSub = vm.matchDay.value!!.minute
        vm.resumeMatch()
        awaitIdle(timeoutMs = 40_000) { (vm.matchDay.value?.minute ?: 0) > minuteAtSub }
        vm.pauseMatch()
        awaitIdle { vm.matchDay.value?.simulating == false }
        assertTrue("The match must not be finished or restarted", !vm.matchDay.value!!.isPlayed)
        assertTrue("The substituted-on player must still be on the pitch", on in vm.liveXi())
        assertFalse("The substituted-off player must not return", off in vm.liveXi())
    }
}
