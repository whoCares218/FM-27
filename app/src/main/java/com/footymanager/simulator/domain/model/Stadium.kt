package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * The user club's stadium.
 *
 * Every club starts at [level] 1, but level 1 is that club's own starting tier:
 * a small club's level-1 ground is far smaller than a giant's. Upgrading the
 * level raises capacity toward the club's reputation-based ceiling, and the cost
 * of each level grows non-linearly, so the last few levels are a genuine
 * long-term investment rather than a few clicks.
 *
 * The ticket price is a real management lever: pricing too high empties seats,
 * pricing too low leaves money on the table.
 */
@Serializable
data class Stadium(
    val name: String,
    /** Current capacity, in seats. */
    val capacity: Int,
    /** Upgrade level, 1..100. All clubs begin at level 1. */
    val level: Int = 1,
    /** Ticket price per seat, in pounds. */
    val ticketPrice: Int = 32,
    val lastAttendance: Int = 0,
    val averageAttendance: Int = 0,
    /** Weeks remaining on an expansion already paid for; 0 when idle. */
    val expansionWeeksRemaining: Int = 0,
    /** Capacity the current expansion will reach when it completes. */
    val expansionTargetCapacity: Int = 0,
    val totalMatchdayIncome: Long = 0,
    /** Level-1 capacity for this club, the base the level curve grows from. */
    val baseCapacity: Int = capacity,
    /** The club's reputation-based capacity ceiling. */
    val maxCapacity: Int = capacity
) {
    val canExpand: Boolean
        get() = level < MAX_LEVEL && expansionWeeksRemaining == 0 && capacity < maxCapacity

    /**
     * Cost of the next expansion. The per-seat cost rises with the club's size,
     * and a steep level multiplier means level 90 -> 91 costs far more than
     * level 1 -> 2.
     */
    val expansionCost: Long
        get() {
            val seats = (nextCapacity - capacity).coerceAtLeast(1)
            val perSeat = 2_600.0 + maxCapacity * 0.06
            val levelFactor = 1.0 + Math.pow((level - 1).toDouble(), 1.25) * 0.10
            return (seats * perSeat * levelFactor).toLong().coerceAtLeast(250_000L)
        }

    /** Capacity the club reaches at the next level. */
    val nextCapacity: Int
        get() = if (level < MAX_LEVEL) capacityForLevel(level + 1) else capacity

    /** Matchday maintenance grows with the level and size of the ground. */
    val maintenancePerMatch: Long
        get() = (capacity.toLong() * (5L + level / 12L)).coerceAtLeast(40_000L)

    /** Capacity implied by a level, interpolating from base to ceiling. */
    fun capacityForLevel(targetLevel: Int): Int {
        if (targetLevel <= 1) return baseCapacity
        if (maxCapacity <= baseCapacity) return baseCapacity
        val fraction = (targetLevel - 1).toDouble() / (MAX_LEVEL - 1)
        val curved = Math.pow(fraction, 0.92)
        val value = baseCapacity + (maxCapacity - baseCapacity) * curved
        return value.roundToInt().coerceIn(baseCapacity, maxCapacity)
    }

    /** A sensible price band for the club's reputation, used by the UI. */
    fun priceBand(reputation: Int): IntRange {
        val base = 18 + reputation / 4
        return (base - 12).coerceAtLeast(8)..(base + 30)
    }

    companion object {
        /** Highest stadium level. */
        const val MAX_LEVEL = 100

        /** Weeks an expansion takes to complete. */
        const val EXPANSION_WEEKS = 6

        /** Capacity ceiling for a club of this reputation. */
        fun maxCapacityForReputation(reputation: Int): Int = when {
            reputation >= 90 -> 105_000
            reputation >= 82 -> 90_000
            reputation >= 74 -> 75_000
            reputation >= 66 -> 55_000
            else -> 38_000
        }

        fun initial(name: String, capacity: Int, reputation: Int): Stadium {
            val ceiling = maxCapacityForReputation(reputation).coerceAtLeast(capacity)
            return Stadium(
                name = name,
                capacity = capacity,
                level = 1,
                ticketPrice = (20 + reputation / 5).coerceIn(15, 70),
                baseCapacity = capacity,
                maxCapacity = ceiling
            )
        }
    }
}

/** A sponsorship the board has been offered for the coming season. */
@Serializable
data class SponsorOffer(
    val id: Long,
    val name: String,
    /** "Global", "National" or "Local" - shown as the tier badge. */
    val tier: String,
    /** Paid immediately on signing. */
    val upfront: Long,
    /** Paid across the season. */
    val seasonal: Long,
    /** Description of the performance bonus condition. */
    val bonusCondition: String,
    val bonusAmount: Long,
    /** Short flavour text describing the trade-off. */
    val risk: String
)

/** A sponsorship deal the club has signed for the current season. */
@Serializable
data class Sponsorship(
    val name: String,
    val tier: String,
    val upfront: Long,
    val seasonal: Long,
    val bonusCondition: String,
    val bonusAmount: Long,
    /** Set once the performance bonus has been paid. */
    val bonusPaid: Boolean = false
) {
    /** The seasonal money is paid in weekly instalments across a 38-week season. */
    val weeklyInstalment: Long get() = seasonal / 38L
}

/**
 * Optional rewarded-ad allowance. Three ads a day, resetting on the calendar
 * day, and never required to play.
 */
@Serializable
data class AdRewardState(
    /** Epoch day the allowance was last refreshed. */
    val allowanceDay: Long = 0,
    val adsWatchedToday: Int = 0,
    val totalAdsWatched: Int = 0,
    val totalRewardsEarned: Long = 0
) {
    val maxPerDay: Int get() = MAX_PER_DAY

    val remainingToday: Int get() = (MAX_PER_DAY - adsWatchedToday).coerceAtLeast(0)

    /** Reward for the next ad, or 0 when the allowance is exhausted. */
    val nextReward: Long
        get() = if (remainingToday <= 0) 0L else REWARDS[adsWatchedToday]

    /** Total still claimable today. */
    val remainingRewardToday: Long
        get() = (adsWatchedToday until MAX_PER_DAY).sumOf { REWARDS[it] }

    fun withDay(epochDay: Long): AdRewardState =
        if (epochDay == allowanceDay) this
        else copy(allowanceDay = epochDay, adsWatchedToday = 0)

    /** Records a completed ad and returns the reward granted. */
    fun recordAd(epochDay: Long): Pair<AdRewardState, Long> {
        val fresh = withDay(epochDay)
        if (fresh.remainingToday <= 0) return fresh to 0L
        val reward = REWARDS[fresh.adsWatchedToday]
        return fresh.copy(
            adsWatchedToday = fresh.adsWatchedToday + 1,
            totalAdsWatched = fresh.totalAdsWatched + 1,
            totalRewardsEarned = fresh.totalRewardsEarned + reward
        ) to reward
    }

    companion object {
        const val MAX_PER_DAY = 3
        /** 3M, 3M, 4M - a maximum of 10M per day. */
        val REWARDS = listOf(3_000_000L, 3_000_000L, 4_000_000L)
        val MAX_DAILY_TOTAL: Long = REWARDS.sum()
    }
}
