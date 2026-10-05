package com.footymanager.simulator

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.data.SaveCodec
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.ContractTerms
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.NegotiationOutcome
import com.footymanager.simulator.domain.model.NegotiationSide
import com.footymanager.simulator.domain.model.NegotiationStatus
import com.footymanager.simulator.domain.model.TransferPackage
import com.footymanager.simulator.viewmodel.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for the transfer desk. They drive the real ViewModel against
 * the in-memory stores so the negotiation records are exercised exactly as they
 * are in the app: created on a bid, updated on a counter, and moved to their
 * terminal bucket on acceptance, rejection or withdrawal.
 */
@RunWith(AndroidJUnit4::class)
class NegotiationTest {

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

    private fun awaitIdle(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("Timed out waiting for the ViewModel to settle")
    }

    private fun startCareer(viewModel: GameViewModel, clubIndex: Int = 0) {
        viewModel.startNewCareer("Desk Boss", ClubDatabase.buildAll()[clubIndex].id, Difficulty.NORMAL)
        awaitIdle { viewModel.career.value != null }
    }

    private fun savedOnDisk(): Career? = runBlocking { saveRepository.load() }

    // ------------------------------------------------------------------ buy

    @Test
    fun `an offer creates a negotiation record`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.searchTransferMarket("", null).first()
        val required = vm.requiredPackageFor(target)
        val wage = vm.expectedWageFor(target)

        // A bid inside the negotiating band keeps the record live.
        vm.makeOfferPackage(
            target.id,
            TransferPackage(fee = (required.fee * 0.80).toLong()),
            ContractTerms(wagePerWeek = wage, contractYears = 3)
        )
        awaitIdle { vm.negotiations().any { it.playerId == target.id } }

        val record = vm.negotiations().first { it.playerId == target.id }
        assertEquals(NegotiationSide.BUY, record.side)
        assertEquals(target.name, record.playerName)
        assertTrue("A countered bid should still be live", record.status.isLive)
        assertTrue("A fresh negotiation starts unread", record.unread)
    }

    @Test
    fun `a counter offer updates the same negotiation without duplicating it`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.searchTransferMarket("", null).first { it.value < 12_000_000L }
        val required = vm.requiredPackageFor(target)
        val wage = vm.expectedWageFor(target)

        // A low bid draws a counter, then we bid again: the desk must show one
        // negotiation for this player, not two.
        vm.makeOfferPackage(
            target.id,
            TransferPackage(fee = (required.fee * 0.80).toLong()),
            ContractTerms(wagePerWeek = wage, contractYears = 3)
        )
        awaitIdle { vm.negotiations().any { it.playerId == target.id } }
        vm.makeOfferPackage(
            target.id,
            TransferPackage(fee = (required.fee * 0.85).toLong()),
            ContractTerms(wagePerWeek = wage, contractYears = 3)
        )
        awaitIdle {
            vm.negotiations().first { it.playerId == target.id }.latestOffer ==
                (required.fee * 0.85).toLong()
        }

        val records = vm.negotiations().filter { it.playerId == target.id }
        assertEquals("Re-bidding must not duplicate the negotiation", 1, records.size)
        assertEquals(
            "The latest offer should track the newest bid",
            (required.fee * 0.85).toLong(),
            records.first().latestOffer
        )
    }

    @Test
    fun `accepted negotiation moves to completed and is recorded in history`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.searchTransferMarket("", null).first()
        val required = vm.requiredPackageFor(target)
        val wage = vm.expectedWageFor(target)

        vm.makeOfferPackage(
            target.id,
            TransferPackage(fee = required.fee),
            ContractTerms(wagePerWeek = wage, contractYears = 3)
        )
        awaitIdle { vm.career.value!!.player(target.id)?.clubId == vm.career.value!!.userClubId }
        awaitIdle { vm.negotiations().any { it.playerId == target.id && it.status == NegotiationStatus.COMPLETED } }

        val record = vm.negotiations().first { it.playerId == target.id }
        assertEquals(NegotiationStatus.COMPLETED, record.status)
        assertEquals(NegotiationOutcome.COMPLETED, record.outcome)
        assertTrue("A completed signing belongs in the history", vm.transferHistory().any { it.playerId == target.id })
    }

    @Test
    fun `a rejected bid moves the negotiation to rejected`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.searchTransferMarket("", null).first()
        // A trivial bid the selling club certainly turns down outright.
        vm.makeOfferPackage(
            target.id,
            TransferPackage(fee = 1L),
            ContractTerms(wagePerWeek = 1L, contractYears = 1)
        )
        awaitIdle {
            vm.negotiations().any {
                it.playerId == target.id && it.status == NegotiationStatus.REJECTED
            }
        }
        val record = vm.negotiations().first { it.playerId == target.id }
        assertEquals(NegotiationStatus.REJECTED, record.status)
        assertEquals(NegotiationOutcome.REJECTED, record.outcome)
    }

    @Test
    fun `a withdrawn offer moves the negotiation to withdrawn`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.searchTransferMarket("", null).first()
        vm.makeOfferPackage(
            target.id,
            TransferPackage(fee = 1L),
            ContractTerms(wagePerWeek = 1_000L, contractYears = 3)
        )
        awaitIdle { vm.career.value!!.pendingOffers.isNotEmpty() }
        val offerId = vm.career.value!!.pendingOffers.last().id
        vm.cancelOffer(offerId)
        awaitIdle {
            vm.negotiations().any {
                it.playerId == target.id && it.status == NegotiationStatus.WITHDRAWN
            }
        }
        assertEquals(
            NegotiationStatus.WITHDRAWN,
            vm.negotiations().first { it.playerId == target.id }.status
        )
    }

    // ----------------------------------------------------------------- sell

    @Test
    fun `a received sell bid creates a sell-side negotiation`() {
        val vm = newViewModel()
        startCareer(vm)
        val player = vm.career.value!!.userSquad
            .sortedByDescending { it.value }
            .first { vm.interestedClubCount(it, vm.askingPriceFor(it)) > 0 }
        val asking = vm.askingPriceFor(player)

        vm.listPlayerForSale(player.id, asking)
        awaitIdle { vm.negotiations().any { it.side == NegotiationSide.SELL && it.playerId == player.id } }

        val record = vm.negotiations().first { it.side == NegotiationSide.SELL && it.playerId == player.id }
        assertEquals(player.name, record.playerName)
        assertTrue("A listing is a live negotiation", record.status.isLive)
    }

    @Test
    fun `accepting a sell bid completes the negotiation and records the sale`() {
        val vm = newViewModel()
        startCareer(vm)
        val player = vm.career.value!!.userSquad
            .sortedByDescending { it.value }
            .first { vm.interestedClubCount(it, vm.askingPriceFor(it)) > 0 }
        vm.listPlayerForSale(player.id, vm.askingPriceFor(player))
        awaitIdle { vm.pendingSale() != null && vm.pendingSale()!!.bids.isNotEmpty() }
        val clubId = vm.pendingSale()!!.bids.first().clubId

        vm.acceptSaleBid(clubId)
        awaitIdle { vm.career.value!!.pendingSale == null }
        awaitIdle {
            vm.negotiations().any {
                it.side == NegotiationSide.SELL && it.playerId == player.id &&
                    it.status == NegotiationStatus.COMPLETED
            }
        }
        val record = vm.negotiations().first { it.side == NegotiationSide.SELL && it.playerId == player.id }
        assertEquals(NegotiationStatus.COMPLETED, record.status)
        assertTrue("The sale belongs in the history", vm.transferHistory().any { it.playerId == player.id })
    }

    @Test
    fun `cancelling a listing withdraws its negotiations`() {
        val vm = newViewModel()
        startCareer(vm)
        val player = vm.career.value!!.userSquad
            .sortedByDescending { it.value }
            .first { vm.interestedClubCount(it, vm.askingPriceFor(it)) > 0 }
        vm.listPlayerForSale(player.id, vm.askingPriceFor(player))
        awaitIdle { vm.pendingSale() != null && vm.pendingSale()!!.bids.isNotEmpty() }

        vm.cancelSale()
        awaitIdle { vm.career.value!!.pendingSale == null }
        awaitIdle {
            vm.negotiations().any {
                it.side == NegotiationSide.SELL && it.playerId == player.id &&
                    it.status == NegotiationStatus.WITHDRAWN
            }
        }
    }

    // --------------------------------------------------------- persistence

    @Test
    fun `negotiation history survives a save load round trip`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.searchTransferMarket("", null).first()
        val required = vm.requiredPackageFor(target)

        vm.makeOfferPackage(
            target.id,
            TransferPackage(fee = required.fee),
            ContractTerms(wagePerWeek = vm.expectedWageFor(target), contractYears = 3)
        )
        awaitIdle { vm.career.value!!.player(target.id)?.clubId == vm.career.value!!.userClubId }
        awaitIdle { savedOnDisk()?.negotiations?.any { it.playerId == target.id } == true }

        val onDisk = savedOnDisk()!!
        assertTrue("Negotiations must be persisted", onDisk.negotiations.isNotEmpty())
        assertTrue("Transfer history must be persisted", onDisk.transferHistory.isNotEmpty())

        val restored = SaveCodec.decode(SaveCodec.encode(onDisk))!!
        assertEquals(onDisk.negotiations, restored.negotiations)
        assertEquals(onDisk.transferHistory, restored.transferHistory)
    }

    @Test
    fun `opening a negotiation marks it read`() {
        val vm = newViewModel()
        startCareer(vm)
        val target = vm.searchTransferMarket("", null).first()
        vm.makeOfferPackage(
            target.id,
            TransferPackage(fee = 1L),
            ContractTerms(wagePerWeek = 1_000L, contractYears = 3)
        )
        awaitIdle { vm.negotiations().any { it.unread } }
        val id = vm.negotiations().first { it.unread }.id
        assertTrue("There should be an unread negotiation", vm.unreadNegotiationCount() > 0)

        vm.markNegotiationRead(id)
        assertFalse("The opened negotiation should be read", vm.negotiationById(id)!!.unread)
    }
}
