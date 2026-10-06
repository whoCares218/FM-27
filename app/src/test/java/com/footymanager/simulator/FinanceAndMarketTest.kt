package com.footymanager.simulator

import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.data.PlayerGenerator
import com.footymanager.simulator.domain.data.SaveCodec
import com.footymanager.simulator.domain.engine.FinanceEngine
import com.footymanager.simulator.domain.engine.SeasonEngine
import com.footymanager.simulator.domain.engine.StadiumEngine
import com.footymanager.simulator.domain.engine.TransferEngine
import com.footymanager.simulator.domain.model.BidStatus
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.ClubFinanceTier
import com.footymanager.simulator.domain.model.ContractTerms
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.FinanceModel
import com.footymanager.simulator.domain.model.LedgerCategory
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.MatchStatus
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.PlayerValuer
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.SaleStatus
import com.footymanager.simulator.domain.model.Stadium
import com.footymanager.simulator.domain.model.TransferPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The economic core of the game: player valuation and wages, club finances,
 * transfer buying and selling negotiation, the stadium, ticket pricing and the
 * per-match financial report.
 *
 * The tests are written to pin down *behaviour that matters to the player* — that
 * value rises steeply with quality and falls with age, that a lowball bid is
 * rejected while a fair one is accepted, that a high asking price thins the field
 * of buyers, that money is conserved across a transfer — rather than exact
 * magic numbers, so the model can be tuned without churning the suite.
 */
class FinanceAndMarketTest {

    private val clubs: List<Club> = ClubDatabase.buildAll()
    private var idSeq = 0L

    private fun newCareer(clubId: Long = clubs.first().id, seed: Long = 4242L): Career =
        CareerFactory.create(
            CareerFactory.NewCareerRequest("Test Manager", clubId, Difficulty.NORMAL, seed)
        )

    private fun player(
        overall: Int,
        age: Int = 24,
        potential: Int = overall,
        position: Position = Position.CM,
        value: Long = 0L,
        wage: Long = 0L
    ): Player {
        val club = clubs.first()
        // Fixed seed so every generated base player shares the same form, stats
        // and contract, letting a test isolate the one attribute it varies.
        val base = PlayerGenerator.generateSquad(club, Random(overall * 31 + 7)) { ++idSeq }
            .first { it.position == position }
        return base.copy(
            overall = overall,
            potential = potential,
            age = age,
            position = position,
            value = if (value > 0) value else base.value,
            wagePerWeek = if (wage > 0) wage else base.wagePerWeek
        )
    }

    // ------------------------------------------------------- market valuation

    @Test
    fun `market value rises steeply with overall rating`() {
        val good = PlayerValuer.marketValue(player(70, age = 24))
        val veryGood = PlayerValuer.marketValue(player(80, age = 24))
        val elite = PlayerValuer.marketValue(player(90, age = 24))
        assertTrue("80 should be worth far more than 70", veryGood > good * 2)
        assertTrue("90 should be worth far more than 80", elite > veryGood * 2)
    }

    @Test
    fun `same-rating players can have very different values`() {
        val prime = PlayerValuer.marketValue(player(80, age = 24, potential = 84))
        val veteran = PlayerValuer.marketValue(player(80, age = 33, potential = 80))
        val prospect = PlayerValuer.marketValue(player(80, age = 19, potential = 92))
        assertTrue("A 33-year-old must be worth much less than a 24-year-old", veteran < prime / 2)
        assertTrue("A high-ceiling teenager should carry a premium", prospect > prime)
    }

    @Test
    fun `value declines with age after the peak`() {
        val values = (24..35).map { PlayerValuer.marketValue(player(82, age = it, potential = 82)) }
        for (i in 1 until values.size) {
            assertTrue("Value should not rise with age: $values", values[i] <= values[i - 1])
        }
        assertTrue("A 35-year-old should be worth a fraction of his peak", values.last() < values.first() / 4)
    }

    @Test
    fun `a player in the final year of his contract is worth less`() {
        val long = PlayerValuer.marketValue(player(80).copy(contractYearsRemaining = 4))
        val short = PlayerValuer.marketValue(player(80).copy(contractYearsRemaining = 1))
        assertTrue("A short contract must reduce the fee", short < long)
    }

    @Test
    fun `an injured player is worth less than a fit one`() {
        val fit = PlayerValuer.marketValue(player(80))
        val injured = PlayerValuer.marketValue(
            player(80).copy(injury = com.footymanager.simulator.domain.model.Injury(
                com.footymanager.simulator.domain.model.InjuryType.KNEE, 6
            ))
        )
        assertTrue("Injury must depress the value", injured < fit)
    }

    @Test
    fun `valuation breakdown explains the number`() {
        val breakdown = PlayerValuer.breakdown(player(84, age = 20, potential = 92))
        assertEquals(PlayerValuer.marketValue(player(84, age = 20, potential = 92)), breakdown.marketValue)
        assertTrue("There should be human-readable reasons", breakdown.notes.isNotEmpty())
        assertTrue("A teenager should be flagged as young", breakdown.notes.any { it.contains("Young") })
    }

    @Test
    fun `wages scale with ability and are far higher for elite players`() {
        val squad = PlayerValuer.weeklyWage(player(65))
        val star = PlayerValuer.weeklyWage(player(75))
        val elite = PlayerValuer.weeklyWage(player(90))
        assertTrue("75 should out-earn 65", star > squad)
        assertTrue("90 should out-earn 75 many times over", elite > star * 3)
        assertTrue("Every player earns something", squad > 0)
    }

    // --------------------------------------------------------- club finances

    @Test
    fun `revenue and budgets differ meaningfully by tier`() {
        val elite = FinanceModel.annualRevenue(92)
        val small = FinanceModel.annualRevenue(55)
        assertTrue("Elite revenue should dwarf a small club", elite > small * 10)

        val eliteBudget = FinanceModel.transferBudgetFor(92, 1)
        val smallBudget = FinanceModel.transferBudgetFor(55, 1)
        assertTrue("Elite clubs should have far larger budgets", eliteBudget > smallBudget * 6)
        assertTrue("A small club should not have an elite budget", smallBudget < 40_000_000L)
    }

    @Test
    fun `tiers are assigned from reputation and league`() {
        assertEquals(ClubFinanceTier.GLOBAL_ELITE, FinanceModel.tierFor(93, 1))
        assertEquals(ClubFinanceTier.LARGE, FinanceModel.tierFor(84, 1))
        assertEquals(ClubFinanceTier.MID_TABLE, FinanceModel.tierFor(68, 1))
        assertEquals(ClubFinanceTier.LOWER_RESOURCE, FinanceModel.tierFor(60, 2))
    }

    @Test
    fun `transfer budget is separate from club balance`() {
        val career = newCareer()
        val club = career.userClub
        // The two are independent quantities; a club can hold cash it cannot spend.
        assertTrue(club.balance > 0)
        assertTrue(club.transferBudget > 0)
        assertNotEquals(club.balance, club.transferBudget)
    }

    @Test
    fun `the squad-cost ratio flags an unsustainable wage bill`() {
        val club = clubs.first()
        val league = League.byId(club.leagueId)
        val normal = FinanceModel.squadCostRatio(
            weeklyWages = FinanceModel.expectedWageBill(club.reputation),
            weeklyAmortisation = 0L,
            reputation = club.reputation,
            leagueTier = league.tier
        )
        val crazy = FinanceModel.squadCostRatio(
            weeklyWages = FinanceModel.expectedWageBill(club.reputation) * 3,
            weeklyAmortisation = 0L,
            reputation = club.reputation,
            leagueTier = league.tier
        )
        assertTrue("A normal bill should be under 100%", normal < 1.0)
        assertTrue("A runaway bill should be well over 100%", crazy > 1.5)
    }

    @Test
    fun `weekly wages reduce the club balance and are recorded`() {
        val career = newCareer()
        val before = career.userClub.balance
        val after = FinanceEngine.payWeeklyWages(career, career.idCounter)
        assertTrue(after.userClub.balance < before)
        assertTrue(after.ledger.any { it.category == LedgerCategory.WAGES })
    }

    @Test
    fun `wages are charged once per week, not once per fixture`() {
        // Regression: wages were once paid inside every fixture result, so a
        // 38-match season charged the wage bill hundreds of times. A full
        // matchday — the user's game plus every other game in the world — must
        // charge wages exactly once.
        val career = newCareer(seed = 42L)
        val bill = career.wageBill(career.userClubId)
        val before = career.userClub.balance
        val match = career.nextMatch()!!
        var current = SeasonEngine.simulateOtherFixtures(career, Random(3))
        val (after, _) = SeasonEngine.simulateFixture(current, match, Random(3), userMatch = true)
        current = SeasonEngine.advanceWeek(after, Random(3))
        val wagesPaid = before - current.userClub.balance
        // The balance moves by wages plus commercial/broadcast income, so the wage
        // charge must be a single week's bill, not dozens of them.
        assertTrue(
            "A matchday should charge roughly one week of wages (paid=$wagesPaid, bill=$bill)",
            wagesPaid < bill * 3
        )
    }

    @Test
    fun `commercial and broadcast income arrives every week`() {
        val career = newCareer()
        val after = FinanceEngine.payWeeklyCommercialIncome(career, career.idCounter)
        assertTrue("Commercial income should be booked", after.ledger.any { it.category == LedgerCategory.COMMERCIAL })
        assertTrue("Broadcast income should be booked", after.ledger.any { it.category == LedgerCategory.BROADCASTING })
    }

    @Test
    fun `finance summary categories reconcile with the ledger`() {
        var career = newCareer(seed = 7L)
        career = FinanceEngine.payWeeklyWages(career, career.idCounter)
        career = FinanceEngine.payWeeklyCommercialIncome(career, career.idCounter)
        val summary = FinanceEngine.summarise(career)
        val seasonLedger = career.ledger.filter { it.season == career.season }
        assertEquals(seasonLedger.filter { it.amount > 0 }.sumOf { it.amount }, summary.totalIncome)
        assertEquals(seasonLedger.filter { it.amount < 0 }.sumOf { -it.amount }, summary.totalExpense)
        assertEquals(summary.totalIncome - summary.totalExpense, summary.netSeason)
    }

    // -------------------------------------------------------------- buying

    @Test
    fun `a lowball offer is rejected but a fair offer is accepted`() {
        var career = newCareer(seed = 222L)
        val target = TransferEngine.marketPlayers(career).first { it.value < 8_000_000L }
        val required = TransferEngine.requiredPackage(career, target, career.userClubId)
        val wage = TransferEngine.expectedWage(career, target, career.userClubId)

        career = TransferEngine.createUserOffer(
            career, target, TransferPackage(fee = 1L),
            ContractTerms(wagePerWeek = wage / 2, contractYears = 3)
        )
        val lowball = career.pendingOffers.last()
        assertEquals(com.footymanager.simulator.domain.model.OfferStatus.REJECTED, lowball.status)

        val spendBefore = career.transferSpendThisSeason
        val budgetBefore = career.userClub.transferBudget
        career = TransferEngine.createUserOffer(
            career, target, TransferPackage(fee = required.fee),
            ContractTerms(wagePerWeek = wage, contractYears = 3)
        )
        career = TransferEngine.resolveOffer(career, career.pendingOffers.last().id, Random(2))
        assertEquals("Player should have joined", career.userClubId, career.player(target.id)?.clubId)
        assertTrue("Spending should rise", career.transferSpendThisSeason > spendBefore)
        assertTrue("Budget should fall", career.userClub.transferBudget < budgetBefore)
    }

    @Test
    fun `a player-plus-cash offer can be accepted`() {
        var career = newCareer(seed = 55L)
        val target = TransferEngine.marketPlayers(career).first { it.value < 12_000_000L }
        val required = TransferEngine.requiredPackage(career, target, career.userClubId)
        // Offer a squad player plus the balance of the fee in cash.
        val makeweight = career.userSquad.first { it.overall < 70 }
        val cash = (required.fee - makeweight.value).coerceAtLeast(0L)
        val wage = TransferEngine.expectedWage(career, target, career.userClubId)
        career = TransferEngine.createUserOffer(
            career,
            target,
            TransferPackage(fee = cash, playerOfferedId = makeweight.id, playerOfferedValue = makeweight.value),
            ContractTerms(wagePerWeek = wage, contractYears = 3)
        )
        career = TransferEngine.resolveOffer(career, career.pendingOffers.last().id, Random(4))
        assertEquals("The target should have joined", career.userClubId, career.player(target.id)?.clubId)
    }

    @Test
    fun `buying a player adds wage pressure and an amortisation charge`() {
        var career = newCareer(seed = 88L)
        val target = TransferEngine.marketPlayers(career).first { it.value < 10_000_000L }
        val required = TransferEngine.requiredPackage(career, target, career.userClubId)
        val wage = TransferEngine.expectedWage(career, target, career.userClubId)
        val billBefore = career.wageBill(career.userClubId)
        val amortBefore = career.weeklyAmortisation
        career = TransferEngine.createUserOffer(
            career, target, TransferPackage(fee = required.fee),
            ContractTerms(wagePerWeek = wage, contractYears = 4)
        )
        career = TransferEngine.resolveOffer(career, career.pendingOffers.last().id, Random(6))
        assertTrue("The wage bill should grow", career.wageBill(career.userClubId) > billBefore)
        assertTrue("A signing should add amortisation", career.weeklyAmortisation > amortBefore)
    }

    // -------------------------------------------------------------- selling

    @Test
    fun `listing a player generates bids clustered around market value`() {
        var career = newCareer(seed = 909L)
        val star = career.userSquad.maxByOrNull { it.value }!!
        career = TransferEngine.listPlayer(career, star.id, star.value, Random(11))
        val sale = career.pendingSale
        assertNotNull("A negotiation should be opened", sale)
        sale!!
        assertTrue("There should be between 0 and 5 interested clubs", sale.bids.size in 0..5)
        for (bid in sale.bids) {
            assertTrue(
                "Bids should be in a realistic band around value, was ${bid.amount} vs ${star.value}",
                bid.amount <= star.value * 1.2 && bid.amount >= star.value / 4
            )
        }
    }

    @Test
    fun `a higher asking price reduces interest`() {
        val career = newCareer(seed = 123L)
        val star = career.userSquad.maxByOrNull { it.value }!!
        val value = star.value
        val cheap = TransferEngine.interestedClubCount(career, star, (value * 0.9).toLong())
        val normal = TransferEngine.interestedClubCount(career, star, value)
        val expensive = TransferEngine.interestedClubCount(career, star, (value * 1.5).toLong())
        assertTrue("A bargain should attract at least as many clubs", cheap >= normal)
        assertTrue("An unrealistic price should thin the field", expensive < normal)
    }

    @Test
    fun `countering a bid produces an immediate club response`() {
        var career = newCareer(seed = 321L)
        val star = career.userSquad.maxByOrNull { it.value }!!
        career = TransferEngine.listPlayer(career, star.id, star.value, Random(13))
        val sale = career.pendingSale!!
        if (sale.bids.isEmpty()) return // no buyers for this seed; nothing to counter
        val bid = sale.bids.first()
        val counter = (bid.amount * 1.02).toLong()
        career = TransferEngine.counterSaleBid(career, bid.clubId, counter, Random(14))
        val updated = career.pendingSale!!.bidFor(bid.clubId)!!
        assertNotEquals("The club must respond to a counter", BidStatus.INTERESTED, updated.status)
        assertTrue(
            "The response should be one of the negotiated outcomes",
            updated.status in setOf(
                BidStatus.ACCEPTED, BidStatus.IMPROVED, BidStatus.REJECTED, BidStatus.WITHDRAWN
            )
        )
    }

    @Test
    fun `accepting a bid completes the sale and pays the club`() {
        var career = newCareer(seed = 444L)
        val star = career.userSquad.maxByOrNull { it.value }!!
        career = TransferEngine.listPlayer(career, star.id, star.value, Random(17))
        val sale = career.pendingSale!!
        if (sale.bids.isEmpty()) return
        val bid = sale.bids.first()
        val balanceBefore = career.userClub.balance
        val incomeBefore = career.transferIncomeThisSeason

        career = TransferEngine.acceptSaleBid(career, bid.clubId)
        assertEquals("The player should have moved", bid.clubId, career.player(star.id)?.clubId)
        assertTrue("The sale should bring money in", career.userClub.balance > balanceBefore)
        assertTrue("Transfer income should be recorded", career.transferIncomeThisSeason > incomeBefore)
        assertTrue("A sale is written to the ledger", career.ledger.any { it.category == LedgerCategory.TRANSFER_OUT })
        assertNull("The listing should be cleared", career.pendingSale)
    }

    @Test
    fun `rejecting a bid removes that club without completing a sale`() {
        var career = newCareer(seed = 555L)
        val star = career.userSquad.maxByOrNull { it.value }!!
        career = TransferEngine.listPlayer(career, star.id, star.value, Random(19))
        val sale = career.pendingSale!!
        if (sale.bids.isEmpty()) return
        val bid = sale.bids.first()
        career = TransferEngine.rejectSaleBid(career, bid.clubId)
        assertEquals(BidStatus.REJECTED, career.pendingSale!!.bidFor(bid.clubId)!!.status)
        assertEquals("The player should still be ours", career.userClubId, career.player(star.id)?.clubId)
    }

    @Test
    fun `cancelling a sale clears the negotiation and listing`() {
        var career = newCareer(seed = 666L)
        val star = career.userSquad.maxByOrNull { it.value }!!
        career = TransferEngine.listPlayer(career, star.id, star.value, Random(23))
        career = TransferEngine.cancelSale(career)
        assertNull(career.pendingSale)
        assertTrue(career.transferListings.none { it.playerId == star.id })
    }

    @Test
    fun `money is conserved when a player is sold`() {
        var career = newCareer(seed = 777L)
        val star = career.userSquad.maxByOrNull { it.value }!!
        career = TransferEngine.listPlayer(career, star.id, star.value, Random(29))
        val sale = career.pendingSale!!
        if (sale.bids.isEmpty()) return
        val bid = sale.bids.first()
        val buyerBefore = career.clubOrThrow(bid.clubId).balance
        val sellerBefore = career.userClub.balance
        career = TransferEngine.acceptSaleBid(career, bid.clubId)
        val buyerAfter = career.clubOrThrow(bid.clubId).balance
        val sellerAfter = career.userClub.balance
        assertEquals("Seller receives exactly the fee", sellerBefore + bid.amount, sellerAfter)
        assertEquals("Buyer pays exactly the fee", buyerBefore - bid.amount, buyerAfter)
    }

    // ------------------------------------------------------------- stadium

    @Test
    fun `every club starts at stadium level one`() {
        val career = newCareer()
        assertEquals(1, career.stadium.level)
        assertTrue(career.stadium.capacity > 0)
    }

    @Test
    fun `level one capacity differs by club size`() {
        val giant = clubs.maxByOrNull { it.reputation }!!
        val small = clubs.minByOrNull { it.reputation }!!
        val giantStadium = Stadium.initial(giant.stadiumName, giant.stadiumCapacity, giant.reputation)
        val smallStadium = Stadium.initial(small.stadiumName, small.stadiumCapacity, small.reputation)
        assertTrue("A big club's level-1 ground should be larger", giantStadium.capacity > smallStadium.capacity)
        assertTrue("A big club should have a higher ceiling", giantStadium.maxCapacity > smallStadium.maxCapacity)
    }

    @Test
    fun `upgrade cost grows non-linearly and level can reach 100`() {
        val base = Stadium.initial("Test Park", 20_000, 80)
        val lowLevel = base.copy(level = 2, capacity = base.capacityForLevel(2))
        val midLevel = base.copy(level = 50, capacity = base.capacityForLevel(50))
        val highLevel = base.copy(level = 95, capacity = base.capacityForLevel(95))
        assertTrue("A high level must cost far more per upgrade", highLevel.expansionCost > midLevel.expansionCost)
        assertTrue("A mid level must cost more than a low level", midLevel.expansionCost > lowLevel.expansionCost)
        assertTrue("Level should be able to reach 100", base.capacityForLevel(100) == base.maxCapacity)
    }

    @Test
    fun `capacity is capped by the club's reputation`() {
        val small = Stadium.initial("Small Park", 8_000, 60)
        assertTrue("A small club cannot grow without limit", small.maxCapacity <= 55_000)
        assertTrue(
            "Capacity never exceeds the ceiling",
            small.capacityForLevel(100) <= small.maxCapacity
        )
    }

    @Test
    fun `completing an expansion raises capacity and level`() {
        val base = Stadium.initial("Test Park", 20_000, 80)
        val (inProgress, cost) = StadiumEngine.beginExpansion(base)
        assertTrue(cost > 0)
        assertEquals(Stadium.EXPANSION_WEEKS, inProgress.expansionWeeksRemaining)
        var stadium = inProgress
        repeat(Stadium.EXPANSION_WEEKS) { stadium = StadiumEngine.progressExpansion(stadium) }
        assertTrue("Capacity should grow", stadium.capacity > base.capacity)
        assertEquals("Level should rise by one", base.level + 1, stadium.level)
    }

    // -------------------------------------------------------- ticket pricing

    @Test
    fun `ticket price affects attendance`() {
        val career = newCareer()
        val stadium = career.stadium
        val cheap = StadiumEngine.estimate(
            stadium.copy(ticketPrice = 15), career.userClub.reputation,
            career.userClub.reputation, 1.5, career.fanSatisfaction
        )
        val dear = StadiumEngine.estimate(
            stadium.copy(ticketPrice = 90), career.userClub.reputation,
            career.userClub.reputation, 1.5, career.fanSatisfaction
        )
        assertTrue("A high price should reduce the crowd", dear.expectedAttendance < cheap.expectedAttendance)
    }

    @Test
    fun `no ticket price dominates - revenue peaks away from the extremes`() {
        val career = newCareer()
        val stadium = career.stadium
        fun revenue(price: Int): Long = StadiumEngine.estimate(
            stadium.copy(ticketPrice = price), career.userClub.reputation,
            career.userClub.reputation, 1.5, career.fanSatisfaction
        ).estimatedTicketRevenue
        val cheap = revenue(8)
        val mid = revenue(35)
        val dear = revenue(120)
        assertTrue("Mid pricing should beat the bargain price", mid > cheap)
        assertTrue("Mid pricing should beat the premium price", mid > dear)
    }

    @Test
    fun `attendance never exceeds capacity`() {
        val career = newCareer()
        val estimate = StadiumEngine.estimate(
            career.stadium.copy(ticketPrice = 5), career.userClub.reputation,
            career.userClub.reputation, 3.0, 100
        )
        assertTrue(estimate.expectedAttendance <= career.stadium.capacity)
    }

    @Test
    fun `the profit-maximising ticket price is interior, not the maximum`() {
        // Regression: a clamped fill made attendance flat above the band, so gate
        // revenue grew with every price rise and the maximum price won. The
        // optimum must sit strictly inside the selectable range (5..150).
        val career = newCareer()
        val stadium = career.stadium
        fun revenue(price: Int): Long = StadiumEngine.estimate(
            stadium.copy(ticketPrice = price), career.userClub.reputation,
            career.userClub.reputation, 1.5, career.fanSatisfaction
        ).estimatedTicketRevenue

        val prices = (5..150 step 5).toList()
        val best = prices.maxByOrNull { revenue(it) }!!
        assertTrue(
            "The best price ($best) must not be the maximum (150)",
            best < 150
        )
        assertTrue("The best price ($best) must not be the minimum (5)", best > 5)
        assertTrue("A mid price must beat the maximum", revenue(best) > revenue(150))
    }

    // ------------------------------------------------- matchday finance

    @Test
    fun `the matchday report accounts for every revenue stream and expense`() {
        val career = newCareer()
        val finance = StadiumEngine.matchdayFinance(
            stadium = career.stadium,
            matchId = 1L,
            opponentName = "Rivals FC",
            competitionLabel = "League",
            attendance = 30_000,
            reputation = career.userClub.reputation
        )
        assertEquals(30_000L * career.stadium.ticketPrice, finance.ticketRevenue)
        assertTrue(finance.hospitalityRevenue > 0)
        assertTrue(finance.concessionsRevenue > 0)
        assertTrue(finance.merchandiseRevenue > 0)
        assertEquals(
            finance.ticketRevenue + finance.hospitalityRevenue + finance.concessionsRevenue +
                finance.merchandiseRevenue + finance.competitionIncome,
            finance.totalRevenue
        )
        assertEquals(
            finance.stadiumOperations + finance.security + finance.staff + finance.maintenance,
            finance.totalExpenses
        )
        assertEquals(finance.totalRevenue - finance.totalExpenses, finance.netProfit)
    }

    @Test
    fun `a home match books matchday revenue and costs`() {
        val career = newCareer(seed = 2024L)
        val match = career.nextMatch()!!
        val (after, _) = SeasonEngine.simulateFixture(career, match, Random(5), userMatch = true)
        val finance = after.lastMatchdayFinance
        assertNotNull("A home match should produce a report", finance)
        assertTrue(after.ledger.any { it.category == LedgerCategory.MATCHDAY })
        assertTrue(after.ledger.any { it.category == LedgerCategory.MATCHDAY_EXPENSES })
    }

    // ---------------------------------------------------- league position

    @Test
    fun `a win moves the club up the table and is reflected in position`() {
        val career = newCareer(seed = 3030L)
        val match = career.nextMatch()!!
        // Mirror the ViewModel: simulate the rest of the matchday, then the user's
        // fixture, so the whole division has played a round.
        val withOthers = SeasonEngine.simulateOtherFixtures(career, Random(7))
        val (after, _) = SeasonEngine.simulateFixture(withOthers, match, Random(7), userMatch = true)
        val afterPosition = after.userLeaguePosition
        val table = after.table.getValue(after.userLeagueId)
        val clubCount = after.clubs.count { it.leagueId == after.userLeagueId }
        assertTrue(
            "A position should be reported (was $afterPosition, table=${table.size}, clubs=$clubCount)",
            afterPosition in 1..clubCount
        )
        // After one matchday every club has played exactly once, so the played
        // column across the whole table must equal the number of clubs.
        assertEquals("Every club should have played once", clubCount, table.sumOf { it.played })
    }

    // ------------------------------------------------------- save / load

    @Test
    fun `financial and stadium state survives a save and load`() {
        var career = newCareer(seed = 808L)
        career = career.copy(stadium = career.stadium.copy(level = 12, ticketPrice = 44))
        career = FinanceEngine.payWeeklyCommercialIncome(career, career.idCounter)
        val star = career.userSquad.maxByOrNull { it.value }!!
        career = TransferEngine.listPlayer(career, star.id, star.value, Random(31))

        val restored = SaveCodec.decode(SaveCodec.encode(career))!!
        assertEquals(career.stadium.level, restored.stadium.level)
        assertEquals(career.stadium.ticketPrice, restored.stadium.ticketPrice)
        assertEquals(career.userClub.balance, restored.userClub.balance)
        assertEquals(career.userClub.transferBudget, restored.userClub.transferBudget)
        assertEquals(career.userClub.wageBudget, restored.userClub.wageBudget)
        assertEquals(career.transferListings, restored.transferListings)
        assertNotNull("A live sale should persist", restored.pendingSale)
        assertEquals(star.id, restored.pendingSale!!.playerId)
        // Player values are part of the serialised state.
        assertEquals(career.player(star.id)!!.value, restored.player(star.id)!!.value)
    }

    // ------------------------------------------------- multi-season economy

    @Test
    fun `the economy stays bounded across multiple seasons`() {
        var career = newCareer(seed = 1234L)
        val random = Random(99)
        val balanceBySeason = mutableListOf<Long>()

        // Play out ten seasons, recording the closing balance each time.
        repeat(10) {
            var guard = 0
            while (career.nextMatch() != null && guard < 200) {
                guard++
                val match = career.nextMatch()!!
                career = SeasonEngine.simulateOtherFixtures(career, random)
                val (after, _) = SeasonEngine.simulateFixture(career, match, random, userMatch = true)
                career = SeasonEngine.advanceWeek(after, random)
            }
            career = SeasonEngine.endSeason(career, random)
            balanceBySeason += career.userClub.balance
            assertTrue(
                "Balance should never explode: ${balanceBySeason.last()}",
                career.userClub.balance < 2_000_000_000L
            )
            assertTrue(
                "Balance should never become permanently impossible: ${balanceBySeason.last()}",
                career.userClub.balance > -200_000_000L
            )
        }
        assertTrue("Financial history should be recorded", career.financialHistory.isNotEmpty())
        assertTrue("Every season should be logged", career.financialHistory.size >= 10)

        // The economy must not diverge: no single season should swing the balance
        // by an implausible amount, and the growth must be bounded.
        val swings = balanceBySeason.zipWithNext { a, b -> kotlin.math.abs(b - a) }
        assertTrue("Season swings should be finite", swings.all { it < 400_000_000L })
        assertTrue(
            "Ten seasons of growth should stay within reason",
            balanceBySeason.last() < 1_500_000_000L
        )
    }

    @Test
    fun `richer clubs stay financially stronger than smaller clubs`() {
        val all = ClubDatabase.buildAll()
        val richest = all.maxByOrNull { it.transferBudget }!!
        val poorest = all.minByOrNull { it.transferBudget }!!
        assertTrue(richest.reputation >= poorest.reputation)
        assertTrue(richest.transferBudget > poorest.transferBudget * 5)
    }
}
