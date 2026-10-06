package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.ClubFinanceTier
import com.footymanager.simulator.domain.model.FinanceLedgerEntry
import com.footymanager.simulator.domain.model.FinanceModel
import com.footymanager.simulator.domain.model.LedgerCategory
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.NewsCategory
import com.footymanager.simulator.domain.model.NewsItem
import com.footymanager.simulator.domain.model.ObjectiveStatus
import com.footymanager.simulator.domain.model.SeasonFinanceRecord
import kotlin.math.roundToLong

/**
 * Weekly wage payments, weekly commercial/broadcast income, season budgets and
 * the board's financial verdict.
 *
 * Money is never created from nothing: every pound the club receives is written
 * to the ledger under a named category, and every pound it spends is written
 * under another. The weekly pass nets operating income against the wage bill so
 * the balance tells a coherent story.
 */
object FinanceEngine {

    /**
     * Pays one week of wages for every club. The user's club also gets a weekly
     * ledger entry so the finances screen tells a clear story, and the board is
     * warned when squad costs run away from revenue.
     */
    fun payWeeklyWages(career: Career, startingId: Long): Career {
        var idCounter = startingId
        val userWageBill = career.wageBill(career.userClubId)

        val updatedClubs = career.clubs.map { club ->
            val bill = career.wageBill(club.id)
            club.copy(balance = club.balance - bill)
        }

        val userClub = updatedClubs.first { it.id == career.userClubId }

        idCounter++
        val entry = FinanceLedgerEntry(
            id = idCounter,
            date = career.date,
            season = career.season,
            description = "Weekly wages (${career.players.count { it.clubId == career.userClubId }} players)",
            amount = -userWageBill,
            category = LedgerCategory.WAGES
        )

        var news = career.news
        val league = League.byId(userClub.leagueId)
        val tier = FinanceModel.tierFor(userClub.reputation, league.tier)
        val ratio = FinanceModel.squadCostRatio(
            weeklyWages = userWageBill,
            weeklyAmortisation = career.weeklyAmortisation,
            reputation = userClub.reputation,
            leagueTier = league.tier
        )

        // Warn the manager when squad costs are becoming dangerous.
        if (ratio > tier.squadCostLimit * 1.10) {
            idCounter++
            news = news + NewsItem(
                id = idCounter,
                category = NewsCategory.BOARD,
                headline = "Board warns over squad costs",
                body = "${career.userClub.name} is spending ${"%.0f".format(ratio * 100)}% of revenue on " +
                    "squad costs, above the board's ${"%.0f".format(tier.squadCostLimit * 100)}% limit. " +
                    "Trim the wage bill or grow revenue.",
                date = career.date,
                season = career.season,
                clubId = career.userClubId
            )
        }
        if (userClub.balance < 0) {
            idCounter++
            news = news + NewsItem(
                id = idCounter,
                category = NewsCategory.BOARD,
                headline = "Board concerned by club finances",
                body = "${career.userClub.name} is now in the red. " +
                    "The board expects the wage bill to be brought under control.",
                date = career.date,
                season = career.season,
                clubId = career.userClubId
            )
        }

        // Financial objective tracking.
        val board = career.board.copy(
            objectives = career.board.objectives.map { obj ->
                if (obj.title == "Finances") {
                    obj.copy(
                        status = when {
                            userClub.balance > 0 && ratio <= tier.squadCostLimit ->
                                ObjectiveStatus.ACHIEVED
                            userClub.balance > 0 && ratio <= tier.squadCostLimit * 1.10 ->
                                ObjectiveStatus.AT_RISK
                            userClub.balance > -5_000_000 -> ObjectiveStatus.AT_RISK
                            else -> ObjectiveStatus.FAILED
                        }
                    )
                } else obj
            }
        )

        return career.copy(
            clubs = updatedClubs,
            ledger = (career.ledger + entry).takeLast(600),
            news = news.takeLast(120),
            board = board,
            players = career.players.map { player ->
                if (player.clubId == null) player
                else player.copy(wagesPaidCareer = player.wagesPaidCareer + player.wagePerWeek)
            },
            idCounter = idCounter
        )
    }

    /**
     * Banks one week of commercial and broadcasting income for the user's club.
     * Together with matchday and sponsorship money this is what makes the season
     * balance a real economy rather than a slow drain.
     */
    fun payWeeklyCommercialIncome(career: Career, startingId: Long): Career {
        var idCounter = startingId
        val club = career.userClub
        val league = League.byId(club.leagueId)
        val tier = FinanceModel.tierFor(club.reputation, league.tier)
        val annual = FinanceModel.annualRevenue(club.reputation)

        // Commercial and broadcast shares scale with the tier and the league.
        val commercialShare = when (tier) {
            ClubFinanceTier.GLOBAL_ELITE -> 0.34
            ClubFinanceTier.LARGE -> 0.30
            ClubFinanceTier.STRONG_DOMESTIC -> 0.27
            ClubFinanceTier.MID_TABLE -> 0.24
            ClubFinanceTier.SMALL_PRO -> 0.20
            ClubFinanceTier.LOWER_RESOURCE -> 0.17
        }
        val broadcastShare = if (league.tier >= 2) 0.14 else 0.30

        val commercial = (annual * commercialShare / 52.0).roundToLong()
        val broadcast = (annual * broadcastShare / 52.0).roundToLong()

        val ledger = mutableListOf<FinanceLedgerEntry>()
        idCounter++
        ledger += FinanceLedgerEntry(
            id = idCounter,
            date = career.date,
            season = career.season,
            description = "Commercial income",
            amount = commercial,
            category = LedgerCategory.COMMERCIAL
        )
        idCounter++
        ledger += FinanceLedgerEntry(
            id = idCounter,
            date = career.date,
            season = career.season,
            description = "Broadcasting income",
            amount = broadcast,
            category = LedgerCategory.BROADCASTING
        )

        val updatedClubs = career.clubs.map {
            if (it.id == career.userClubId) it.copy(balance = it.balance + commercial + broadcast) else it
        }

        return career.copy(
            clubs = updatedClubs,
            ledger = (career.ledger + ledger).takeLast(600),
            idCounter = idCounter
        )
    }

    /** Adjusts the board's confidence in response to the club's finances. */
    fun applyFinancialPressure(career: Career): Career {
        val club = career.userClub
        if (club.balance >= 0) return career
        val confidenceDrop = ((-club.balance) / 2_000_000L).toInt().coerceIn(0, 6)
        if (confidenceDrop == 0) return career
        return career.copy(
            board = career.board.copy(
                confidence = (career.board.confidence - confidenceDrop).coerceAtLeast(0)
            )
        )
    }

    /** One week of non-wage operating costs for the user's club. */
    fun weeklyOperatingCost(career: Career): Long {
        val club = career.userClub
        val league = League.byId(club.leagueId)
        val tier = FinanceModel.tierFor(club.reputation, league.tier)
        return (FinanceModel.annualRevenue(club.reputation) * tier.operatingCostRatio / 52.0).roundToLong()
    }

    /**
     * Charges one week of non-wage operating costs (staff, academy, scouting,
     * administration, utilities). Wages are the biggest line but not the only
     * one; without this the club would bank its entire commercial income and the
     * balance would drift upward forever.
     *
     * The rate is a share of the club's own revenue tier, so a small club pays a
     * small bill and an elite club a large one.
     */
    fun payWeeklyOperatingCosts(career: Career, startingId: Long): Career {
        var idCounter = startingId
        val weekly = weeklyOperatingCost(career)

        idCounter++
        val entry = FinanceLedgerEntry(
            id = idCounter,
            date = career.date,
            season = career.season,
            description = "Club operating costs",
            amount = -weekly,
            category = LedgerCategory.OTHER
        )
        val updatedClubs = career.clubs.map {
            if (it.id == career.userClubId) it.copy(balance = it.balance - weekly) else it
        }
        return career.copy(
            clubs = updatedClubs,
            ledger = (career.ledger + entry).takeLast(600),
            idCounter = idCounter
        )
    }
    /**
     * Recalculates budgets at the start of a new season. The balance is carried
     * over untouched — the club earns its money during the season through
     * matchday, commercial, broadcast, sponsorship and prize money, so there is
     * no automatic cash injection just because a new season starts.
     */
    fun refreshSeasonBudgets(club: Club, seasonNumber: Int): Club {
        val league = League.byId(club.leagueId)
        return club.copy(
            transferBudget = FinanceModel.transferBudgetFor(club.reputation, league.tier),
            wageBudget = FinanceModel.wageBudgetFor(club.reputation)
        )
    }

    /**
     * Cash the club can actually commit to a transfer right now, for the finance
     * screen's "available cash" line: the board's transfer budget, but never more
     * than the club's own balance. This is the honest cash view, not the spending
     * rule — see [canAfford].
     */
    fun availableTransferCash(club: Club): Long =
        minOf(club.transferBudget, club.balance).coerceAtLeast(0L)

    /**
     * The board's spending rule: a transfer is affordable while it fits inside the
     * transfer budget the board set for the season. The budget — not the raw cash
     * balance — is the authority, exactly as the transfer UI presents it, so a
     * fully-funded bid is never rejected at the last moment and a club cannot sign
     * a player it was never allowed to buy.
     */
    fun canAfford(club: Club, fee: Long): Boolean =
        fee <= club.transferBudget

    /** True when the wage fits inside the remaining wage budget. */
    fun canAffordWage(career: Career, club: Club, weeklyWage: Long): Boolean {
        val currentBill = career.wageBill(club.id)
        return currentBill + weeklyWage <= club.wageBudget * 1.25
    }

    /** Total outstanding transfer amortisation, in pounds per week. */
    fun weeklyAmortisation(career: Career): Long = career.weeklyAmortisation

    /** The club's current squad-cost ratio, on the UEFA-style basis. */
    fun squadCostRatio(career: Career): Double {
        val club = career.userClub
        val league = League.byId(club.leagueId)
        return FinanceModel.squadCostRatio(
            weeklyWages = career.wageBill(club.id),
            weeklyAmortisation = career.weeklyAmortisation,
            reputation = club.reputation,
            leagueTier = league.tier
        )
    }

    /** One month of the club's ledger, used by the finances chart. */
    data class MonthlyFinancePoint(
        val label: String,
        val month: Int,
        val income: Long,
        val expense: Long
    ) {
        val net: Long get() = income - expense
    }

    /** Revenue summary used by the finances screen. */
    data class FinanceSummary(
        val balance: Long,
        val transferBudget: Long,
        val wageBudget: Long,
        val weeklyWageBill: Long,
        val transferSpend: Long,
        val transferIncome: Long,
        val matchdayRevenue: Long,
        val prizeMoney: Long,
        val wageSpend: Long,
        val sponsorshipRevenue: Long = 0L,
        val commercialRevenue: Long = 0L,
        val stadiumSpend: Long = 0L,
        val matchdayExpense: Long = 0L,
        val otherIncome: Long = 0L,
        val otherExpense: Long = 0L,
        val tier: ClubFinanceTier = ClubFinanceTier.MID_TABLE,
        val squadCostRatio: Double = 0.0,
        val squadCostLimit: Double = 0.85,
        val weeklyAmortisation: Long = 0L,
        val monthly: List<MonthlyFinancePoint> = emptyList(),
        /** Ledger-derived totals, so the summary can never disagree with the books. */
        val totalIncome: Long = 0L,
        val totalExpense: Long = 0L,
        /**
         * The rest of the season's committed running cost (wages, operating costs
         * and amortisation for the matchdays still to play). Shown so the manager
         * can see what the club has already promised to pay.
         */
        val projectedSeasonCost: Long = 0L,
        /** Transfer budget the board would still release in cash, capped by the balance. */
        val availableCash: Long = 0L
    ) {
        val netTransfer: Long get() = transferIncome - transferSpend
        val wageHeadroom: Long get() = wageBudget - weeklyWageBill
        val isWageBillHealthy: Boolean get() = weeklyWageBill <= wageBudget

        val netSeason: Long get() = totalIncome - totalExpense

        val squadCostVerdict: String
            get() = FinanceModel.squadCostVerdict(squadCostRatio, squadCostLimit)

        val squadCostPercent: Int get() = (squadCostRatio * 100).roundToLong().toInt()
    }

    fun summarise(career: Career): FinanceSummary {
        val club = career.userClub
        val league = League.byId(club.leagueId)
        val seasonLedger = career.ledger.filter { it.season == career.season }
        val monthly = seasonLedger
            .groupBy { it.date.month }
            .map { (month, entries) ->
                MonthlyFinancePoint(
                    label = MONTH_SHORT[month - 1],
                    month = month,
                    income = entries.filter { it.amount > 0 }.sumOf { it.amount },
                    expense = entries.filter { it.amount < 0 }.sumOf { -it.amount }
                )
            }
            .sortedBy { it.month }

        fun sumOf(category: LedgerCategory): Long =
            seasonLedger.filter { it.category == category }.sumOf { it.amount }

        val matchday = seasonLedger
            .filter { it.category.isMatchdayRevenue }
            .sumOf { it.amount }

        // Totals are taken straight from the ledger, split by sign. That makes it
        // impossible for the summary screen to disagree with the transactions.
        val totalIncome = seasonLedger.filter { it.amount > 0 }.sumOf { it.amount }
        val totalExpense = seasonLedger.filter { it.amount < 0 }.sumOf { -it.amount }

        val transferIncome = sumOf(LedgerCategory.TRANSFER_OUT)
        val transferSpend = -sumOf(LedgerCategory.TRANSFER_IN)
        val wageSpend = -sumOf(LedgerCategory.WAGES)
        val stadiumSpend = -sumOf(LedgerCategory.STADIUM)
        val matchdayExpense = -sumOf(LedgerCategory.MATCHDAY_EXPENSES)
        val sponsorship = sumOf(LedgerCategory.SPONSORSHIP)
        val commercial = sumOf(LedgerCategory.COMMERCIAL) + sumOf(LedgerCategory.BROADCASTING)
        val prize = sumOf(LedgerCategory.PRIZE_MONEY) + sumOf(LedgerCategory.COMPETITION_REVENUE)

        // Whatever is left is shown as "other", so every category is accounted for.
        val otherIncome = (totalIncome - matchday - transferIncome - sponsorship - commercial - prize)
            .coerceAtLeast(0L)
        val otherExpense = (totalExpense - wageSpend - stadiumSpend - matchdayExpense - transferSpend)
            .coerceAtLeast(0L)

        val tier = FinanceModel.tierFor(club.reputation, league.tier)

        // What the club has already committed to pay over the rest of the season.
        val remainingWeeks = (career.totalMatchdays() - career.matchdayIndex).coerceAtLeast(0)
        val projectedCost = (career.wageBill(club.id) + career.weeklyAmortisation +
            weeklyOperatingCost(career)) * remainingWeeks

        return FinanceSummary(
            balance = club.balance,
            transferBudget = club.transferBudget,
            wageBudget = club.wageBudget,
            weeklyWageBill = career.wageBill(club.id),
            transferSpend = transferSpend,
            transferIncome = transferIncome,
            matchdayRevenue = matchday,
            prizeMoney = prize,
            wageSpend = wageSpend,
            sponsorshipRevenue = sponsorship,
            commercialRevenue = commercial,
            stadiumSpend = stadiumSpend,
            matchdayExpense = matchdayExpense,
            otherIncome = otherIncome,
            otherExpense = otherExpense,
            tier = tier,
            squadCostRatio = squadCostRatio(career),
            squadCostLimit = tier.squadCostLimit,
            weeklyAmortisation = career.weeklyAmortisation,
            monthly = monthly,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            projectedSeasonCost = projectedCost,
            availableCash = availableTransferCash(club)
        )
    }

    /** Captures the closing position of the season for the financial history. */
    fun recordSeason(career: Career): Career {
        val summary = summarise(career)
        val seasonLedger = career.ledger.filter { it.season == career.season }
        val otherExpense = -seasonLedger
            .filter { it.amount < 0 && it.category == LedgerCategory.OTHER }
            .sumOf { it.amount }
        val record = SeasonFinanceRecord(
            season = career.season,
            seasonNumber = career.seasonNumber,
            matchdayRevenue = summary.matchdayRevenue,
            sponsorshipRevenue = summary.sponsorshipRevenue,
            prizeMoney = summary.prizeMoney,
            commercialRevenue = summary.commercialRevenue,
            transferIncome = summary.transferIncome,
            wageSpend = summary.wageSpend,
            transferSpend = summary.transferSpend,
            stadiumSpend = summary.stadiumSpend,
            otherExpense = summary.matchdayExpense + otherExpense,
            closingBalance = summary.balance
        )
        val history = (career.financialHistory.filter { it.seasonNumber != career.seasonNumber } + record)
            .sortedBy { it.seasonNumber }
        return career.copy(financialHistory = history)
    }

    private val MONTH_SHORT = listOf(
        "Aug", "Sep", "Oct", "Nov", "Dec", "Jan",
        "Feb", "Mar", "Apr", "May", "Jun", "Jul"
    )
}
