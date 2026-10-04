package com.footymanager.simulator

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.footymanager.simulator.domain.data.AnimationSpeed
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.GamePhase
import com.footymanager.simulator.domain.model.Mentality
import com.footymanager.simulator.domain.model.OfferStatus
import com.footymanager.simulator.domain.model.PlayStyle
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.Tempo
import com.footymanager.simulator.domain.model.TrainingFocus
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
 * Integration tests for the ViewModel: the object that actually wires the engines
 * to the UI.
 *
 * `viewModelScope` runs on `Dispatchers.Main`, which Robolectric pauses, so the
 * main dispatcher is swapped for a real one. Storage is an in-memory
 * [FakeCareerStore]; the real DataStore round-trip is covered by `SaveLoadTest`.
 */
@RunWith(AndroidJUnit4::class)
class GameViewModelTest {

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

    /** The career as it currently exists on disk, bypassing the ViewModel cache. */
    private fun savedOnDisk(): Career? = runBlocking { saveRepository.load() }

    /** Waits until [condition] holds, polling the ViewModel and the store. */
    private fun awaitIdle(timeoutMs: Long = 15_000, condition: () -> Boolean) {
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

    /** Starts a career and waits until it is safely on disk. */
    private fun startCareerAndPersist(viewModel: GameViewModel, clubIndex: Int = 0): Long {
        startCareer(viewModel, clubIndex)
        val clubId = viewModel.career.value!!.userClubId
        awaitIdle { savedOnDisk()?.userClubId == clubId }
        return clubId
    }

    /** Plays every remaining fixture through the quick-sim path. */
    private fun playOutSeason(viewModel: GameViewModel) {
        var guard = 0
        while (viewModel.career.value!!.nextMatch() != null && guard < 200) {
            guard++
            val matchday = viewModel.career.value!!.matchdayIndex
            viewModel.quickSimNextMatch()
            awaitIdle { viewModel.career.value!!.matchdayIndex > matchday }
        }
    }

    @Test
    fun `starting a new career produces a playable club`() {
        val vm = newViewModel()
        startCareer(vm)
        val career = vm.career.value!!
        assertEquals("Test Boss", career.managerName)
        assertTrue("Squad should be filled", career.userSquad.size >= 18)
        assertNotNull("A first fixture should exist", career.nextMatch())
        assertTrue("The board should set objectives", career.board.objectives.isNotEmpty())
    }

    @Test
    fun `a new career is written to disk and can be continued`() {
        val vm = newViewModel()
        val clubId = startCareerAndPersist(vm, clubIndex = 1)

        // A second ViewModel simulates the app being restarted.
        val restarted = newViewModel()
        restarted.continueCareer()
        awaitIdle { restarted.career.value != null }
        assertEquals("The same club should be restored", clubId, restarted.career.value!!.userClubId)
    }

    @Test
    fun `hasSave reflects whether a career exists`() {
        val vm = newViewModel()
        vm.resetCareer()
        awaitIdle { !vm.hasSave.value && savedOnDisk() == null }
        assertFalse("A reset career should report no save", vm.hasSave.value)

        startCareer(vm)
        awaitIdle { vm.hasSave.value }
        assertTrue("A started career should report a save", vm.hasSave.value)
    }

    @Test
    fun `playing a match through the view model records a result and advances the week`() {
        val vm = newViewModel()
        startCareer(vm)
        val before = vm.career.value!!
        val matchdayBefore = before.matchdayIndex
        val fixture = before.nextMatch()!!

        assertTrue("prepareNextMatch should succeed", vm.prepareNextMatch())
        val matchDay = vm.matchDay.value
        assertNotNull("Match-day state should exist", matchDay)
        assertEquals(fixture.id, matchDay!!.match.id)
        assertFalse("The match should not be played yet", matchDay.isPlayed)

        vm.playMatch()
        awaitIdle { vm.matchDay.value?.isPlayed == true }
        val result = vm.matchDay.value!!.result
        assertNotNull("A result should be produced", result)
        assertTrue("Goals must be non-negative", result!!.homeGoals >= 0 && result.awayGoals >= 0)
        assertTrue("Events should be generated", result.events.isNotEmpty())
        assertTrue("Player ratings should be produced", result.playerRatings.isNotEmpty())

        vm.advanceAfterMatch()
        awaitIdle { vm.career.value!!.matchdayIndex > matchdayBefore }
        assertEquals(
            "The calendar should advance by one matchday",
            matchdayBefore + 1,
            vm.career.value!!.matchdayIndex
        )
        assertTrue("The result should be stored", vm.career.value!!.results.any { it.matchId == fixture.id })
    }

    @Test
    fun `abandoning an unplayed match leaves the fixture as the next match`() {
        val vm = newViewModel()
        startCareer(vm)
        val fixture = vm.career.value!!.nextMatch()!!
        assertTrue(vm.prepareNextMatch())
        vm.cancelMatch()
        assertNull("Match-day state should be cleared", vm.matchDay.value)
        assertEquals(
            "The same fixture should still be next",
            fixture.id,
            vm.career.value!!.nextMatch()!!.id
        )
        assertEquals("The calendar must not advance", 0, vm.career.value!!.matchdayIndex)
    }

    @Test
    fun `quick sim plays and advances in one step`() {
        val vm = newViewModel()
        startCareer(vm)
        vm.quickSimNextMatch()
        awaitIdle { vm.career.value!!.matchdayIndex == 1 }
        assertEquals(1, vm.career.value!!.matchdayIndex)
        assertTrue("A result should be recorded", vm.career.value!!.results.isNotEmpty())
    }

    @Test
    fun `tactics changes are persisted to the save`() {
        val vm = newViewModel()
        startCareer(vm)
        vm.setFormation("3-5-2")
        vm.setMentality(Mentality.ATTACKING)
        vm.setStyle(PlayStyle.HIGH_PRESS)
        vm.setTempo(Tempo.FAST)
        vm.setTrainingFocus(TrainingFocus.DEFENCE)

        awaitIdle {
            savedOnDisk()?.tactics?.formationId == "3-5-2" &&
                savedOnDisk()?.trainingFocus == TrainingFocus.DEFENCE &&
                savedOnDisk()?.tactics?.tempo == Tempo.FAST
        }

        val restored = savedOnDisk()!!
        assertEquals("3-5-2", restored.tactics.formationId)
        assertEquals(Mentality.ATTACKING, restored.tactics.mentality)
        assertEquals(PlayStyle.HIGH_PRESS, restored.tactics.style)
        assertEquals(Tempo.FAST, restored.tactics.tempo)
        assertEquals(TrainingFocus.DEFENCE, restored.trainingFocus)
    }

    @Test
    fun `changing formation repairs the starting eleven`() {
        val vm = newViewModel()
        startCareer(vm)
        // Every formation must leave a legal, complete XI.
        for (formation in listOf("4-3-3", "4-2-3-1", "4-4-2", "3-5-2", "3-4-3", "5-3-2")) {
            vm.setFormation(formation)
            awaitIdle { vm.career.value!!.tactics.formationId == formation }
            val career = vm.career.value!!
            val selection = career.selection
            assertEquals("$formation must field eleven players", 11, selection.startingXi.size)
            assertTrue(
                "$formation must only field available players",
                selection.startingXi.all { career.player(it.playerId)?.isAvailable == true }
            )
        }
    }

    @Test
    fun `auto pick always produces a legal eleven`() {
        val vm = newViewModel()
        startCareer(vm)
        vm.autoPickSelection()
        awaitIdle { vm.career.value!!.selection.startingXi.size == 11 }
        val selection = vm.career.value!!.selection
        assertEquals(11, selection.startingXi.size)
        assertEquals("No player may be picked twice", 11, selection.startingXi.map { it.playerId }.toSet().size)
    }

    @Test
    fun `the captain can be changed and is remembered`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.career.value!!.selection.startingXi.first().playerId
        vm.setCaptain(target)
        awaitIdle { savedOnDisk()?.selection?.captainId == target }
        assertEquals(target, savedOnDisk()!!.selection.captainId)
    }

    @Test
    fun `a transfer can be negotiated through the view model`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.searchTransferMarket("", null).first()
        val required = vm.requiredPackageFor(target)
        val wage = vm.expectedWageFor(target)

        // The bid is evaluated immediately: a full-price package should complete
        // the signing in a single call, with no waiting for another in-game day.
        vm.makeOfferPackage(
            target.id,
            com.footymanager.simulator.domain.model.TransferPackage(fee = required.fee),
            com.footymanager.simulator.domain.model.ContractTerms(wagePerWeek = wage, contractYears = 3)
        )
        awaitIdle { vm.career.value!!.player(target.id)?.clubId == vm.career.value!!.userClubId }

        assertEquals(
            "The player should now be at the user's club",
            vm.career.value!!.userClubId,
            vm.career.value!!.player(target.id)?.clubId
        )
        assertTrue("A news item should be generated", vm.career.value!!.news.isNotEmpty())
    }

    @Test
    fun `a player plus cash package can sign a target`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.searchTransferMarket("", null).first { it.value < 6_000_000L }
        val required = vm.requiredPackageFor(target)
        val makeweight = vm.career.value!!.userSquad.maxByOrNull { it.value }!!
        val cash = (required.fee - makeweight.value).coerceAtLeast(0L)
        val wage = vm.expectedWageFor(target)

        vm.makeOfferPackage(
            target.id,
            com.footymanager.simulator.domain.model.TransferPackage(
                fee = cash,
                playerOfferedId = makeweight.id,
                playerOfferedName = makeweight.name,
                playerOfferedValue = makeweight.value
            ),
            com.footymanager.simulator.domain.model.ContractTerms(wagePerWeek = wage, contractYears = 3)
        )
        // The selling club must respond immediately, never leaving it pending.
        val offer = vm.career.value!!.pendingOffers.last()
        assertTrue(
            "The selling club must respond at once",
            offer.sellingClubResponse != null
        )
    }

    @Test
    fun `a counter offer is presented immediately`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.searchTransferMarket("", null).first { it.value < 10_000_000L }
        val required = vm.requiredPackageFor(target)
        val wage = vm.expectedWageFor(target)

        // A bid around 80% of the valuation should draw a counter, not a wait.
        vm.makeOfferPackage(
            target.id,
            com.footymanager.simulator.domain.model.TransferPackage(fee = (required.fee * 0.80).toLong()),
            com.footymanager.simulator.domain.model.ContractTerms(wagePerWeek = wage, contractYears = 3)
        )
        val offer = vm.career.value!!.pendingOffers.last()
        assertEquals(
            "An 80% bid should trigger a counter-offer",
            com.footymanager.simulator.domain.model.SellingClubResponse.NEGOTIATE,
            offer.sellingClubResponse
        )
        assertNotNull("A counter package should be shown", offer.counterPackage)

        // Accepting the counter should immediately try the player and complete.
        vm.acceptCounter(offer.id)
        awaitIdle { vm.career.value!!.player(target.id)?.clubId == vm.career.value!!.userClubId }
        assertEquals(
            "Accepting the counter should complete the signing",
            vm.career.value!!.userClubId,
            vm.career.value!!.player(target.id)?.clubId
        )
    }

    @Test
    fun `an offer can be withdrawn`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.searchTransferMarket("", null).first()
        // A deliberately inadequate bid so the deal stays open to withdraw.
        vm.makeOfferPackage(
            target.id,
            com.footymanager.simulator.domain.model.TransferPackage(fee = 1L),
            com.footymanager.simulator.domain.model.ContractTerms(wagePerWeek = 1_000L, contractYears = 3)
        )
        awaitIdle { vm.career.value!!.pendingOffers.isNotEmpty() }
        val offerId = vm.career.value!!.pendingOffers.last().id
        vm.cancelOffer(offerId)
        awaitIdle {
            vm.career.value!!.pendingOffers.first { it.id == offerId }.status == OfferStatus.WITHDRAWN
        }
        assertEquals(
            OfferStatus.WITHDRAWN,
            vm.career.value!!.pendingOffers.first { it.id == offerId }.status
        )
    }

    @Test
    fun `transfer search filters by name and by position`() {
        val vm = newViewModel()
        startCareer(vm)
        val all = vm.searchTransferMarket("", null)
        assertTrue("The market should not be empty", all.isNotEmpty())

        val byPosition = vm.searchTransferMarket("", Position.ST)
        assertTrue("There should be strikers available", byPosition.isNotEmpty())
        assertTrue("Filter must be respected", byPosition.all { it.position == Position.ST })

        val name = all.first().name
        val byName = vm.searchTransferMarket(name, null)
        assertTrue("Searching a name should find that player", byName.any { it.name == name })
    }

    @Test
    fun `transfer search can match a club name`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.searchTransferMarket("", null).first()
        val clubName = vm.career.value!!.club(target.clubId!!)!!.name
        val results = vm.searchTransferMarket(clubName, null)
        assertTrue("Searching a club should return its players", results.isNotEmpty())
        assertTrue(
            "Every hit must belong to that club",
            results.all { it.clubId == target.clubId }
        )
    }

    @Test
    fun `the market never offers the user's own players`() {
        val vm = newViewModel()
        startCareer(vm)
        val market = vm.searchTransferMarket("", null)
        assertTrue(
            "The market must exclude your own squad",
            market.none { it.clubId == vm.career.value!!.userClubId }
        )
    }

    @Test
    fun `a player can be sold and the money arrives`() {
        val vm = newViewModel()
        startCareer(vm)
        val player = vm.career.value!!.userSquad.maxByOrNull { it.value }!!
        val buyers = vm.interestedBuyers(player.id)
        assertTrue("There should be interest in a valuable player", buyers.isNotEmpty())
        val (buyer, fee) = buyers.first()
        val balanceBefore = vm.career.value!!.userClub.balance

        vm.sellPlayer(player.id, fee, buyer.id)
        awaitIdle { vm.career.value!!.player(player.id)?.clubId == buyer.id }
        assertTrue("Transfer income should rise", vm.career.value!!.transferIncomeThisSeason > 0)
        assertTrue("The balance should improve", vm.career.value!!.userClub.balance > balanceBefore)
        assertTrue(
            "The player should leave the squad",
            vm.career.value!!.userSquad.none { it.id == player.id }
        )
    }

    @Test
    fun `settings persist across a restart`() {
        val vm = newViewModel()
        vm.setSound(false)
        vm.setDarkTheme(false)
        vm.setDifficulty(Difficulty.HARD)
        vm.setAnimationSpeed(AnimationSpeed.FAST)

        val restarted = newViewModel()
        awaitIdle {
            val s = restarted.settings.value
            !s.soundEnabled && !s.darkTheme && s.difficulty == Difficulty.HARD
        }
        val settings = restarted.settings.value
        assertFalse("Sound setting should persist", settings.soundEnabled)
        assertFalse("Theme setting should persist", settings.darkTheme)
        assertEquals(Difficulty.HARD, settings.difficulty)
        assertEquals(AnimationSpeed.FAST, settings.animationSpeed)
    }

    @Test
    fun `a full season can be played through the view model and rolls over`() {
        val vm = newViewModel()
        startCareer(vm)
        val totalMatchdays = vm.career.value!!.totalMatchdays()

        playOutSeason(vm)

        val ended = vm.career.value!!
        assertEquals("Every matchday should be played", totalMatchdays, ended.matchdayIndex)

        vm.startNextSeason()
        awaitIdle { vm.career.value!!.seasonNumber == 2 }
        val next = vm.career.value!!
        assertEquals(2, next.seasonNumber)
        assertEquals("2027/28", next.season)
        assertEquals(0, next.matchdayIndex)
        assertTrue("New fixtures should be generated", next.fixtures.isNotEmpty())
        assertNotNull("A season summary should be stored", next.lastSeasonSummary)
    }

    @Test
    fun `returning to the main menu saves the career`() {
        val vm = newViewModel()
        startCareerAndPersist(vm)
        vm.quickSimNextMatch()
        awaitIdle { savedOnDisk()?.matchdayIndex == 1 }

        vm.returnToMainMenu()
        awaitIdle { vm.career.value == null }

        val restarted = newViewModel()
        restarted.continueCareer()
        awaitIdle { restarted.career.value != null }
        assertEquals(
            "Progress made before returning to the menu must persist",
            1,
            restarted.career.value!!.matchdayIndex
        )
    }

    @Test
    fun `resetting a career removes the save and prevents continuing`() {
        val vm = newViewModel()
        startCareerAndPersist(vm)
        vm.resetCareer()
        awaitIdle { vm.career.value == null && !vm.hasSave.value && savedOnDisk() == null }

        val restarted = newViewModel()
        restarted.continueCareer()
        awaitIdle { restarted.message.value != null }
        assertNull("There should be nothing to continue", restarted.career.value)
    }

    @Test
    fun `season end phase is reached when the calendar is exhausted`() {
        val vm = newViewModel()
        startCareer(vm)
        playOutSeason(vm)
        assertEquals(
            "The phase should flip to season ended",
            GamePhase.SEASON_ENDED,
            vm.career.value!!.phase
        )
    }

    @Test
    fun `swapping two tactical slots exchanges the two players`() {
        val vm = newViewModel()
        startCareer(vm)
        vm.autoPickSelection()
        awaitIdle { vm.career.value!!.selection.startingXi.size == 11 }

        val before = vm.career.value!!.selection.startingXi.associate { it.slotIndex to it.playerId }
        val a = before.keys.min()
        val b = before.keys.max()

        vm.swapSlots(a, b)
        awaitIdle {
            val now = vm.career.value!!.selection.startingXi.associate { it.slotIndex to it.playerId }
            now[a] == before[b] && now[b] == before[a]
        }

        val after = vm.career.value!!.selection.startingXi.associate { it.slotIndex to it.playerId }
        assertEquals("Slot A should now hold B's player", before[b], after[a])
        assertEquals("Slot B should now hold A's player", before[a], after[b])
        assertEquals("No player should be lost", 11, after.size)
    }

    @Test
    fun `swapping a slot with itself leaves the lineup unchanged`() {
        val vm = newViewModel()
        startCareer(vm)
        vm.autoPickSelection()
        awaitIdle { vm.career.value!!.selection.startingXi.size == 11 }

        val before = vm.career.value!!.selection.startingXi
        vm.swapSlots(3, 3)
        Thread.sleep(120)
        assertEquals(before, vm.career.value!!.selection.startingXi)
    }
}
