package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.ChampionsLeagueState
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.KnockoutRound
import com.footymanager.simulator.domain.model.europeanCompetitions
import com.footymanager.simulator.domain.model.isEuropean

/** How a club currently stands in a continental competition. */
enum class EuropeanStatusKind {
    /** The club is not in any European competition this season. */
    NOT_PARTICIPATING,
    /** League phase running: a table position is meaningful. */
    LEAGUE_POSITION,
    /** Knockout football: the current round is the meaningful state. */
    STAGE,
    /** Knocked out; [EuropeanStatus.detail] names the round. */
    ELIMINATED,
    /** Lifted the trophy. */
    WINNER
}

/**
 * A club's status in one continental competition, in a form the UI can render
 * without re-deriving it. [detail] is the human-readable middle column, e.g.
 * "Position: 11th", "Round of 16", "Eliminated — Quarter-Final" or "Winner".
 */
data class EuropeanStatus(
    val competition: CompetitionType?,
    val kind: EuropeanStatusKind,
    val detail: String
) {
    val participating: Boolean get() = kind != EuropeanStatusKind.NOT_PARTICIPATING

    companion object {
        val NONE = EuropeanStatus(null, EuropeanStatusKind.NOT_PARTICIPATING, "Not participating")
    }
}

/** A club's standing in its domestic league. */
data class DomesticStatus(
    val leagueName: String,
    val position: Int,
    val played: Int,
    val complete: Boolean,
    /** The league champion's name once every fixture has been played. */
    val championName: String?
)

/**
 * Derives a club's live competitive status from the persisted career.
 *
 * All of this is read straight from the competition tables and knockout ties, so
 * a screen can never show a stage or a position the data does not support. The
 * simulate-to-date progress panel and the summary both use these helpers, which
 * is what keeps their wording identical to the actual competition state.
 */
object CompetitionStatus {

    /** Display name for a knockout round, matching the UI's house style. */
    fun roundLabel(round: KnockoutRound): String = when (round) {
        KnockoutRound.PLAYOFF -> "Knockout Play-offs"
        KnockoutRound.R16 -> "Round of 16"
        KnockoutRound.QUARTER_FINAL -> "Quarter-Final"
        KnockoutRound.SEMI_FINAL -> "Semi-Final"
        KnockoutRound.FINAL -> "Final"
    }

    fun domesticStatus(career: Career, clubId: Long): DomesticStatus {
        val league = career.club(clubId)?.leagueId?.let {
            com.footymanager.simulator.domain.model.League.byId(it)
        }
        val leagueId = league?.id ?: career.userLeagueId
        val row = career.sortedTable(leagueId).firstOrNull { it.clubId == clubId }
        val leagueFixtures = career.fixtures.filter { it.leagueId == leagueId }
        val complete = leagueFixtures.isNotEmpty() && leagueFixtures.all { it.isPlayed }
        val champion = if (complete) {
            career.sortedTable(leagueId).firstOrNull()?.clubId?.let { career.club(it)?.name }
        } else null
        return DomesticStatus(
            leagueName = league?.name ?: "League",
            position = career.positionOf(clubId, leagueId),
            played = row?.played ?: 0,
            complete = complete,
            championName = champion
        )
    }

    /**
     * The continental competition the club is in this season, and how far it has
     * gone. A club plays in at most one, so the first match wins.
     */
    fun europeanStatus(career: Career, clubId: Long): EuropeanStatus {
        for (competition in europeanCompetitions) {
            val state = career.europeanState(competition)
            if (statusFor(state, clubId).participating) return statusFor(state, clubId)
        }
        return EuropeanStatus.NONE
    }

    fun statusFor(state: ChampionsLeagueState, clubId: Long): EuropeanStatus {
        if (!state.active || !state.participantIds.contains(clubId)) {
            return EuropeanStatus(state.competition, EuropeanStatusKind.NOT_PARTICIPATING, "Not participating")
        }
        // A decided competition: either the club won it or it went out somewhere.
        val winner = state.winnerClubId
        if (winner != null) {
            return if (winner == clubId) {
                EuropeanStatus(state.competition, EuropeanStatusKind.WINNER, "Winner")
            } else {
                val exit = furthestRound(state, clubId)?.let { roundLabel(it) }
                EuropeanStatus(
                    state.competition,
                    EuropeanStatusKind.ELIMINATED,
                    if (exit != null) "Eliminated — $exit" else "Eliminated"
                )
            }
        }
        // League phase still running: a table position is the honest answer.
        if (!state.isLeaguePhaseComplete) {
            val position = state.positionOf(clubId)
            return EuropeanStatus(
                state.competition,
                EuropeanStatusKind.LEAGUE_POSITION,
                if (position == 0) "In progress" else "Position: ${ordinal(position)}"
            )
        }
        // Knockout football: report the furthest round the club has reached.
        val userTies = state.ties.filter { it.highSeedClubId == clubId || it.lowSeedClubId == clubId }
        val latest = userTies.maxByOrNull { it.round.order }
        if (latest == null) {
            // League phase complete but the bracket is not drawn yet, or the club
            // missed the cut. Position still explains it.
            val position = state.positionOf(clubId)
            val detail = when {
                position in 1..8 -> "Round of 16"
                position in 9..24 -> "Knockout Play-offs"
                position > 0 -> "Eliminated — League Phase"
                else -> "In progress"
            }
            val kind = when {
                position in 1..24 -> EuropeanStatusKind.STAGE
                else -> EuropeanStatusKind.ELIMINATED
            }
            return EuropeanStatus(state.competition, kind, detail)
        }
        val lost = latest.decided && latest.winnerClubId != clubId
        return if (lost) {
            EuropeanStatus(state.competition, EuropeanStatusKind.ELIMINATED, "Eliminated — ${roundLabel(latest.round)}")
        } else {
            EuropeanStatus(state.competition, EuropeanStatusKind.STAGE, roundLabel(latest.round))
        }
    }

    /** The latest knockout round the club appeared in. */
    private fun furthestRound(state: ChampionsLeagueState, clubId: Long): KnockoutRound? =
        state.ties
            .filter { it.highSeedClubId == clubId || it.lowSeedClubId == clubId }
            .maxByOrNull { it.round.order }
            ?.round

    /** The competition the club is currently playing in, if any. */
    fun userEuropeanCompetition(career: Career): CompetitionType? =
        europeanCompetitions.firstOrNull {
            career.europeanState(it).active &&
                career.europeanState(it).participantIds.contains(career.userClubId)
        }

    private fun ordinal(n: Int): String {
        val suffix = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }
}

/** True for the competitions whose state lives in a [ChampionsLeagueState]. */
internal fun CompetitionType.hasLeaguePhase(): Boolean = isEuropean
