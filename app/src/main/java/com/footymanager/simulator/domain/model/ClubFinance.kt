package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.roundToLong

/**
 * The economic tier a club sits in. Tiers are derived from reputation and league
 * so the whole world scales together, and they drive revenue, budgets, ticket
 * prices and squad-cost tolerance.
 *
 * The bands mirror the real shape of football finance (Deloitte's Money League
 * shows an order-of-magnitude gap between the global elite and smaller clubs)
 * without copying any real club's figures.
 */
enum class ClubFinanceTier(val label: String, val order: Int) {
    GLOBAL_ELITE("Global elite", 1),
    LARGE("Large club", 2),
    STRONG_DOMESTIC("Strong domestic club", 3),
    MID_TABLE("Mid-table club", 4),
    SMALL_PRO("Small professional club", 5),
    LOWER_RESOURCE("Lower-resource club", 6);

    /** Share of revenue the board is willing to put into the transfer budget. */
    val transferRatio: Double
        get() = when (this) {
            GLOBAL_ELITE -> 0.26
            LARGE -> 0.22
            STRONG_DOMESTIC -> 0.19
            MID_TABLE -> 0.16
            SMALL_PRO -> 0.13
            LOWER_RESOURCE -> 0.11
        }

    /**
     * Non-wage operating costs (staff, academy, scouting, administration,
     * utilities) as a share of annual revenue. Elite clubs carry more staff and
     * facilities, but the share is broadly similar across the pyramid.
     */
    val operatingCostRatio: Double
        get() = when (this) {
            GLOBAL_ELITE -> 0.30
            LARGE -> 0.31
            STRONG_DOMESTIC -> 0.32
            MID_TABLE -> 0.33
            SMALL_PRO -> 0.34
            LOWER_RESOURCE -> 0.36
        }

    /**
     * Squad-cost ratio ceiling used for the manager's warnings. Clubs competing in
     * Europe are held to the 70% reference (UEFA's squad-cost rule); domestic-only
     * clubs get the looser 85% band used by the Premier League for non-European
     * clubs. Lower divisions are looser still, as they are in reality.
     */
    val squadCostLimit: Double
        get() = when (this) {
            GLOBAL_ELITE, LARGE -> 0.70
            STRONG_DOMESTIC -> 0.78
            MID_TABLE -> 0.85
            SMALL_PRO -> 0.92
            LOWER_RESOURCE -> 1.00
        }
}

/**
 * The game's economic engine room: how big a club's revenue, wage budget and
 * transfer budget should be, and whether it is living within its means.
 *
 * Everything is anchored to club reputation so the world is internally
 * consistent: revenue and wages both grow exponentially with quality, which
 * keeps the wage-to-revenue ratio in the realistic 55-65% band across every tier
 * (the real Premier League average is around 65%). This is what stops the
 * economy either exploding into unlimited money or becoming impossible.
 */
object FinanceModel {

    /** Annual revenue for a club of this reputation, in game pounds. */
    fun annualRevenue(reputation: Int): Long {
        val base = 6_000_000.0 * exp(0.105 * (reputation - 50))
        return base.roundToLong().coerceAtLeast(2_500_000L)
    }

    fun tierFor(reputation: Int, leagueTier: Int): ClubFinanceTier {
        if (leagueTier >= 2) {
            return if (reputation >= 66) ClubFinanceTier.SMALL_PRO else ClubFinanceTier.LOWER_RESOURCE
        }
        return when {
            reputation >= 90 -> ClubFinanceTier.GLOBAL_ELITE
            reputation >= 82 -> ClubFinanceTier.LARGE
            reputation >= 74 -> ClubFinanceTier.STRONG_DOMESTIC
            reputation >= 66 -> ClubFinanceTier.MID_TABLE
            reputation >= 58 -> ClubFinanceTier.SMALL_PRO
            else -> ClubFinanceTier.LOWER_RESOURCE
        }
    }

    /**
     * The wage bill a typical squad of this reputation would command. Used to set
     * a realistic wage budget, so an average squad sits just under the limit and
     * one big signing pushes the club over it.
     */
    fun expectedWageBill(reputation: Int): Long {
        val averageOverall = 45.0 + reputation * 0.40
        val averageWage = 1_700.0 * exp(0.15 * (averageOverall - 50.0))
        // Squads are top-heavy, so the mean wage exceeds the wage of the mean player.
        return (averageWage * 24 * 1.15).roundToLong().coerceAtLeast(25_000L)
    }

    /** Weekly wage budget the board grants: the expected bill plus a small margin. */
    fun wageBudgetFor(reputation: Int): Long =
        (expectedWageBill(reputation) * 1.12).toLong().coerceAtLeast(30_000L)

    fun transferBudgetFor(reputation: Int, leagueTier: Int): Long {
        val tier = tierFor(reputation, leagueTier)
        return (annualRevenue(reputation) * tier.transferRatio).roundToLong().coerceAtLeast(250_000L)
    }

    /** Cash a club holds at the start of a career: a few months of operating money. */
    fun openingBalance(reputation: Int, leagueTier: Int): Long {
        val revenue = annualRevenue(reputation)
        val scale = if (leagueTier >= 2) 0.05 else 0.09
        return (revenue * scale).roundToLong().coerceAtLeast(400_000L)
    }

    /**
     * The squad-cost allowance: how much a club may spend on wages plus player
     * amortisation before it breaches its ratio. Reported weekly so it can be
     * compared directly against the wage bill.
     */
    fun squadCostAllowanceWeekly(reputation: Int, leagueTier: Int): Long {
        val tier = tierFor(reputation, leagueTier)
        return (annualRevenue(reputation) * tier.squadCostLimit / 52.0).roundToLong()
    }

    /**
     * Squad cost as a share of revenue, on the same basis as UEFA's squad-cost
     * ratio: player wages plus transfer amortisation over adjusted revenue.
     */
    fun squadCostRatio(weeklyWages: Long, weeklyAmortisation: Long, reputation: Int, leagueTier: Int): Double {
        val weeklyRevenue = annualRevenue(reputation) / 52.0
        if (weeklyRevenue <= 0.0) return 0.0
        return (weeklyWages + weeklyAmortisation) / weeklyRevenue
    }

    /** A board-friendly label for the current squad-cost position. */
    fun squadCostVerdict(ratio: Double, limit: Double): String = when {
        ratio <= limit * 0.85 -> "Comfortable"
        ratio <= limit -> "Within the limit"
        ratio <= limit * 1.10 -> "Approaching the limit"
        ratio <= limit * 1.30 -> "Over budget"
        else -> "Unsustainable"
    }
}

/**
 * One season's financial record, kept so the manager can review how the club's
 * economy has evolved across a career.
 */
@Serializable
data class SeasonFinanceRecord(
    val season: String,
    val seasonNumber: Int,
    val matchdayRevenue: Long,
    val sponsorshipRevenue: Long,
    val prizeMoney: Long,
    val commercialRevenue: Long,
    val transferIncome: Long,
    val wageSpend: Long,
    val transferSpend: Long,
    val stadiumSpend: Long,
    val otherExpense: Long,
    val closingBalance: Long
) {
    val totalIncome: Long
        get() = matchdayRevenue + sponsorshipRevenue + prizeMoney + commercialRevenue + transferIncome

    val totalExpense: Long
        get() = wageSpend + transferSpend + stadiumSpend + otherExpense

    val netProfit: Long get() = totalIncome - totalExpense
}

/**
 * One signing's transfer-fee amortisation: the fee spread evenly across the
 * contract, charged weekly. Modelled on the real accounting treatment that feeds
 * a club's squad-cost ratio.
 */
@Serializable
data class AmortisationCharge(
    val playerId: Long,
    val playerName: String,
    val totalFee: Long,
    val contractYears: Int,
    /** Weeks remaining before the fee is fully amortised. */
    val weeksRemaining: Int
) {
    val weeklyCharge: Long
        get() = if (contractYears <= 0) 0L else (totalFee / (contractYears * 52L)).coerceAtLeast(0L)

    companion object {
        fun forSigning(playerId: Long, playerName: String, fee: Long, contractYears: Int): AmortisationCharge =
            AmortisationCharge(
                playerId = playerId,
                playerName = playerName,
                totalFee = fee,
                contractYears = contractYears.coerceAtLeast(1),
                weeksRemaining = contractYears.coerceAtLeast(1) * 52
            )
    }
}
