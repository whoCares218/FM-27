package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Stadium
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Stadium economics: how many people turn up, what that earns, and how an
 * expansion progresses. Ticket pricing is a real decision - raising the price
 * lifts revenue per seat but empties the ground, which costs atmosphere and,
 * over time, fan satisfaction.
 */
object StadiumEngine {

    /**
     * Attendance for a home match.
     *
     * Demand rises with the club's reputation and the attractiveness of the
     * opponent, and falls as the ticket price climbs above the club's natural
     * price band. A modest home run also helps fill the ground.
     */
    fun attendance(
        stadium: Stadium,
        reputation: Int,
        opponentReputation: Int,
        recentPointsPerGame: Double,
        random: Random
    ): Int {
        val band = stadium.priceBand(reputation)
        val midPrice = (band.first + band.last) / 2.0

        // Price sensitivity: above the midpoint of the band, demand drops off.
        val priceDelta = (stadium.ticketPrice - midPrice) / midPrice
        val priceFactor = (1.0 - priceDelta * 0.55).coerceIn(0.42, 1.12)

        val reputationFactor = 0.55 + reputation / 160.0
        val opponentFactor = 1.0 + (opponentReputation - reputation) / 260.0
        val formFactor = (0.9 + recentPointsPerGame * 0.06).coerceIn(0.85, 1.14)
        val noise = 1.0 + random.nextDouble(-0.05, 0.05)

        val fill = (reputationFactor * opponentFactor * formFactor * priceFactor * noise)
            .coerceIn(0.28, 0.99)

        return (stadium.capacity * fill).roundToInt().coerceIn(0, stadium.capacity)
    }

    /** Gate receipts for an attendance at the current ticket price. */
    fun matchdayIncome(attendance: Int, ticketPrice: Int): Long =
        attendance.toLong() * ticketPrice

    /**
     * Matchday running costs: stewarding, floodlights, catering and pitch
     * maintenance. Larger grounds cost more to open.
     */
    fun matchdayExpenses(stadium: Stadium, attendance: Int): Long {
        val fixed = stadium.capacity.toLong() * 6L
        val perHead = attendance.toLong() * 3L
        return fixed + perHead
    }

    /**
     * Fan satisfaction (0..100) drifts toward a level set by pricing and results.
     * Called once per matchday.
     */
    fun fanSatisfaction(
        current: Int,
        stadium: Stadium,
        reputation: Int,
        recentPointsPerGame: Double,
        random: Random
    ): Int {
        val band = stadium.priceBand(reputation)
        val midPrice = (band.first + band.last) / 2.0
        val pricePenalty = if (stadium.ticketPrice > midPrice) {
            ((stadium.ticketPrice - midPrice) / midPrice * 18.0).roundToInt()
        } else 0

        val target = (62 - pricePenalty + recentPointsPerGame * 8.0 + random.nextDouble(-3.0, 3.0))
            .coerceIn(15.0, 96.0)

        // Move a fifth of the way toward the target each week.
        val moved = current + (target - current) * 0.22
        return moved.roundToInt().coerceIn(0, 100)
    }

    /** Advances an in-progress expansion by one week. */
    fun progressExpansion(stadium: Stadium): Stadium {
        if (stadium.expansionWeeksRemaining <= 0) return stadium
        val remaining = stadium.expansionWeeksRemaining - 1
        return if (remaining <= 0) {
            stadium.copy(
                capacity = stadium.expansionTargetCapacity.coerceAtLeast(stadium.capacity),
                level = (stadium.level + 1).coerceAtMost(Stadium.MAX_CAPACITY.size),
                expansionWeeksRemaining = 0,
                expansionTargetCapacity = 0
            )
        } else {
            stadium.copy(expansionWeeksRemaining = remaining)
        }
    }

    /** Begins an expansion, returning the stadium and the cost deducted. */
    fun beginExpansion(stadium: Stadium): Pair<Stadium, Long> {
        if (!stadium.canExpand) return stadium to 0L
        val cost = stadium.expansionCost
        val target = stadium.nextCapacity
        return stadium.copy(
            expansionWeeksRemaining = Stadium.EXPANSION_WEEKS,
            expansionTargetCapacity = target
        ) to cost
    }

    /** Rolling average attendance across the season. */
    fun updatedAverage(previousAverage: Int, newAttendance: Int, matchesPlayed: Int): Int {
        if (matchesPlayed <= 0) return newAttendance
        return ((previousAverage.toLong() * matchesPlayed + newAttendance) / (matchesPlayed + 1)).toInt()
    }
}
