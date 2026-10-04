package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

/**
 * The complete financial story of one home match, kept so the post-match report
 * can show every revenue stream and cost rather than a single net figure.
 *
 * Stored on the [Career] so it survives a save/load and can be reviewed after the
 * match-day screen is dismissed.
 */
@Serializable
data class MatchdayFinance(
    val matchId: Long,
    val opponentName: String,
    val competitionLabel: String,
    val attendance: Int,
    val capacity: Int,
    val ticketPrice: Int,
    val ticketRevenue: Long,
    val hospitalityRevenue: Long,
    val concessionsRevenue: Long,
    val merchandiseRevenue: Long,
    val matchdayExpenses: Long,
    val stadiumOperations: Long,
    val security: Long,
    val staff: Long,
    val maintenance: Long,
    /** Competition income banked alongside this fixture (e.g. prize/broadcast). */
    val competitionIncome: Long = 0L
) {
    val totalRevenue: Long
        get() = ticketRevenue + hospitalityRevenue + concessionsRevenue +
            merchandiseRevenue + competitionIncome

    val totalExpenses: Long get() = matchdayExpenses

    val netProfit: Long get() = totalRevenue - totalExpenses

    val occupancyPercent: Double
        get() = if (capacity <= 0) 0.0 else attendance * 100.0 / capacity
}

/**
 * A forward estimate of the next home match's gate, shown before kick-off so the
 * manager can judge their ticket pricing.
 */
@Serializable
data class AttendanceEstimate(
    val expectedAttendance: Int,
    val capacity: Int,
    val ticketPrice: Int,
    val estimatedTicketRevenue: Long
) {
    val occupancyPercent: Double
        get() = if (capacity <= 0) 0.0 else expectedAttendance * 100.0 / capacity
}

/**
 * A before/after snapshot of the user's league position for the post-match
 * animation. [before] and [after] are 1-based; 0 means "not in the table".
 */
@Serializable
data class PositionChange(
    val before: Int,
    val after: Int
) {
    val movedUp: Boolean get() = after in 1..before - 1 && before > 0
    val movedDown: Boolean get() = before in 1..after - 1 && after > 0
    val unchanged: Boolean get() = before == after
    val change: Int get() = before - after
}
