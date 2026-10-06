package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.AttendanceEstimate
import com.footymanager.simulator.domain.model.MatchdayFinance
import com.footymanager.simulator.domain.model.Stadium
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Stadium economics: how many people turn up, what that earns, and how an
 * expansion progresses.
 *
 * Ticket pricing is a real decision with no dominant strategy. Raising the price
 * lifts revenue per seat but the demand curve empties the ground, and a full
 * ground sells more hospitality, food and merchandise. There is a sweet spot the
 * manager has to find rather than a "max price wins" button.
 */
object StadiumEngine {

    /**
     * Attendance for a home match.
     *
     * Demand rises with the club's reputation, the attractiveness of the
     * opponent and how well the team is playing, and falls as the ticket price
     * climbs above the club's natural price band. A happy fanbase turns up in
     * greater numbers; a big cup night fills more of the ground than a routine
     * league game.
     */
    fun attendance(
        stadium: Stadium,
        reputation: Int,
        opponentReputation: Int,
        recentPointsPerGame: Double,
        random: Random,
        fanSatisfaction: Int = 60,
        isRival: Boolean = false,
        competitionFactor: Double = 1.0
    ): Int {
        val band = stadium.priceBand(reputation)
        val midPrice = (band.first + band.last) / 2.0

        // Price sensitivity: above the midpoint of the band, demand drops off.
        val priceDelta = (stadium.ticketPrice - midPrice) / midPrice
        val priceFactor = (1.0 - priceDelta * 0.85).coerceIn(0.12, 1.15)

        val reputationFactor = 0.55 + reputation / 160.0
        val opponentFactor = 1.0 + (opponentReputation - reputation) / 260.0
        val formFactor = (0.9 + recentPointsPerGame * 0.06).coerceIn(0.85, 1.14)
        // A satisfied crowd is worth roughly +/- 12% of the gate.
        val fanFactor = (0.88 + fanSatisfaction / 100.0 * 0.24).coerceIn(0.82, 1.14)
        val rivalryFactor = if (isRival) 1.08 else 1.0
        val noise = 1.0 + random.nextDouble(-0.05, 0.05)

        // The non-price factors set the *base* turnout, clamped so a routine game
        // is never empty and a big one never overflows. The price factor is then
        // applied multiplicatively on top and is NOT re-clamped: capping the
        // combined fill made attendance flat once price rose past the band, so
        // gate receipts kept climbing with price and the maximum ticket price
        // became the profit-maximising choice. Letting demand keep falling gives
        // a proper interior optimum near the middle of the band.
        val baseFill = (reputationFactor * opponentFactor * formFactor * fanFactor *
            rivalryFactor * competitionFactor * noise)
            .coerceIn(0.28, 1.0)
        val fill = baseFill * priceFactor

        return (stadium.capacity * fill).roundToInt().coerceIn(0, stadium.capacity)
    }

    /** Forward estimate of the next home gate, shown before kick-off. */
    fun estimate(
        stadium: Stadium,
        reputation: Int,
        opponentReputation: Int,
        recentPointsPerGame: Double,
        fanSatisfaction: Int,
        isRival: Boolean = false,
        competitionFactor: Double = 1.0
    ): AttendanceEstimate {
        // Use a deterministic mid-point (no noise) so the preview does not flicker.
        val expected = attendance(
            stadium = stadium,
            reputation = reputation,
            opponentReputation = opponentReputation,
            recentPointsPerGame = recentPointsPerGame,
            random = Random(0),
            fanSatisfaction = fanSatisfaction,
            isRival = isRival,
            competitionFactor = competitionFactor
        )
        return AttendanceEstimate(
            expectedAttendance = expected,
            capacity = stadium.capacity,
            ticketPrice = stadium.ticketPrice,
            estimatedTicketRevenue = matchdayIncome(expected, stadium.ticketPrice)
        )
    }

    /** Gate receipts for an attendance at the current ticket price. */
    fun matchdayIncome(attendance: Int, ticketPrice: Int): Long =
        attendance.toLong() * ticketPrice

    /**
     * The full matchday financial report: every revenue stream and cost, so the
     * post-match summary can show exactly how much the fixture generated.
     *
     * Hospitality, concessions and merchandise all scale with the crowd, and the
     * higher tiers of hospitality scale with the ground's level.
     */
    fun matchdayFinance(
        stadium: Stadium,
        matchId: Long,
        opponentName: String,
        competitionLabel: String,
        attendance: Int,
        reputation: Int,
        competitionIncome: Long = 0L
    ): MatchdayFinance {
        val ticketRevenue = matchdayIncome(attendance, stadium.ticketPrice)
        val premiumShare = (0.010 + stadium.level * 0.0006).coerceAtMost(0.05)
        val hospitality = (attendance * stadium.ticketPrice * premiumShare).toLong() +
            (stadium.level * 12_000L)
        val concessions = (attendance * 7L)
        val merchandise = (attendance * (2.0 + reputation / 45.0)).toLong()

        val operations = stadium.maintenancePerMatch
        val security = (attendance * 1.6).toLong() + 25_000L
        val staff = (attendance * 2.2).toLong() + 40_000L
        val maintenance = (stadium.capacity * 1.4).toLong() + stadium.level * 3_000L
        val expenses = operations + security + staff + maintenance

        return MatchdayFinance(
            matchId = matchId,
            opponentName = opponentName,
            competitionLabel = competitionLabel,
            attendance = attendance,
            capacity = stadium.capacity,
            ticketPrice = stadium.ticketPrice,
            ticketRevenue = ticketRevenue,
            hospitalityRevenue = hospitality,
            concessionsRevenue = concessions,
            merchandiseRevenue = merchandise,
            matchdayExpenses = expenses,
            stadiumOperations = operations,
            security = security,
            staff = staff,
            maintenance = maintenance,
            competitionIncome = competitionIncome
        )
    }

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
                level = (stadium.level + 1).coerceAtMost(Stadium.MAX_LEVEL),
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
