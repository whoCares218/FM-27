package com.footymanager.simulator.domain.model

import kotlinx.serialization.Serializable

/**
 * The state of a domestic knockout cup for one season.
 *
 * A single-elimination bracket of 32 clubs: five rounds from the Round of 32 to
 * the Final, each a one-off tie decided by extra time and penalties. The bracket
 * is stored as an ordered list of [entrants] (slot index -> club id) so a round's
 * fixtures are always paired from adjacent slots, which makes the progression
 * deterministic and easy to render as a bracket.
 */
@Serializable
data class CupState(
    val season: String = "",
    /** Rounds completed so far (0 before a ball is kicked). */
    val round: Int = 0,
    /** The 32 first-round slots, in bracket order. */
    val entrants: List<Long> = emptyList(),
    /** Every cup fixture id generated so far, in creation order. */
    val fixtureIds: List<Long> = emptyList(),
    /** The club that lifted the cup, once the final has been decided. */
    val winnerClubId: Long? = null,
    val active: Boolean = false
) {
    companion object {
        const val ROUNDS = 5

        /** An inactive cup state for a career that has not started one. */
        val EMPTY = CupState()
    }
}

/** Human label for a cup round number (1..5). */
fun cupRoundLabel(round: Int): String = when (round) {
    1 -> "Round of 32"
    2 -> "Round of 16"
    3 -> "Quarter-final"
    4 -> "Semi-final"
    5 -> "Final"
    else -> "Round $round"
}
