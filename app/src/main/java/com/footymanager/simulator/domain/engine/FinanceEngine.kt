package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.FinanceLedgerEntry
import com.footymanager.simulator.domain.model.LedgerCategory
import com.footymanager.simulator.domain.model.NewsCategory
import com.footymanager.simulator.domain.model.NewsItem
import com.footymanager.simulator.domain.model.ObjectiveStatus
import kotlin.math.roundToLong

/**
 * Weekly wage payments, matchday revenue, season budgets and the board's
 * financial verdict.
 */
object FinanceEngine {

    /**
     * Pays one week of wages for every club. The user's club also gets a weekly
     * ledger entry so the finances screen tells a clear story.
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
        // Warn the manager when the club starts losing money.
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
                            userClub.balance > 0 && userWageBill <= userClub.wageBudget ->
                                ObjectiveStatus.ACHIEVED
                            userClub.balance > 0 -> ObjectiveStatus.AT_RISK
                            userClub.balance > -5_000_000 -> ObjectiveStatus.AT_RISK
                            else -> ObjectiveStatus.FAILED
                        }
                    )
                } else obj
            }
        )

        return career.copy(
            clubs = updatedClubs,
            ledger = (career.ledger + entry).takeLast(400),
            news = news.takeLast(120),
            board = board,
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

    /**
     * Recalculates budgets at the start of a new season: prize money, the board's
     * investment, and a budget that reflects the club's new division.
     */
    fun refreshSeasonBudgets(club: Club, seasonNumber: Int): Club {
        val league = com.footymanager.simulator.domain.model.League.byId(club.leagueId)
        val reputationFactor = club.reputation / 100.0
        val baseBudget = when {
            league.tier == 2 -> (club.reputation * club.reputation * 2_600L)
            club.reputation >= 88 -> (club.reputation * club.reputation * 280_000L)
            club.reputation >= 78 -> (club.reputation * club.reputation * 160_000L)
            club.reputation >= 70 -> (club.reputation * club.reputation * 95_000L)
            else -> (club.reputation * club.reputation * 48_000L)
        }
        // Budgets grow modestly each season to reflect football inflation.
        val inflation = 1.0 + (seasonNumber - 1) * 0.06
        val transferBudget = (baseBudget * inflation).roundToLong().coerceAtLeast(500_000L)
        val wageBudget = (transferBudget * 11L / 100L).coerceAtLeast(150_000L)

        return club.copy(
            transferBudget = transferBudget,
            wageBudget = wageBudget,
            balance = (club.balance + transferBudget / 3).coerceAtLeast(2_000_000L)
        )
    }

    /** True when the club can afford a fee without going deeply into the red. */
    fun canAfford(club: Club, fee: Long): Boolean =
        club.transferBudget >= fee && club.balance + club.transferBudget >= fee

    /** True when the wage fits inside the remaining wage budget. */
    fun canAffordWage(career: Career, club: Club, weeklyWage: Long): Boolean {
        val currentBill = career.wageBill(club.id)
        return currentBill + weeklyWage <= club.wageBudget * 1.25
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
        val wageSpend: Long
    ) {
        val netTransfer: Long get() = transferIncome - transferSpend
        val wageHeadroom: Long get() = wageBudget - weeklyWageBill
        val isWageBillHealthy: Boolean get() = weeklyWageBill <= wageBudget
    }

    fun summarise(career: Career): FinanceSummary {
        val club = career.userClub
        return FinanceSummary(
            balance = club.balance,
            transferBudget = club.transferBudget,
            wageBudget = club.wageBudget,
            weeklyWageBill = career.wageBill(club.id),
            transferSpend = career.transferSpendThisSeason,
            transferIncome = career.transferIncomeThisSeason,
            matchdayRevenue = career.ledger
                .filter { it.category == LedgerCategory.MATCHDAY && it.season == career.season }
                .sumOf { it.amount },
            prizeMoney = career.ledger
                .filter { it.category == LedgerCategory.PRIZE_MONEY && it.season == career.season }
                .sumOf { it.amount },
            wageSpend = career.ledger
                .filter { it.category == LedgerCategory.WAGES && it.season == career.season }
                .sumOf { -it.amount }
        )
    }
}
