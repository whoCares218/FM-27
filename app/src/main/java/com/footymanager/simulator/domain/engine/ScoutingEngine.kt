package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.Player
import com.footymanager.simulator.domain.model.Position

/**
 * A key player in the opponent's ranks, surfaced on the pre-match screen.
 *
 * [reason] explains *why* the player is worth watching (top scorer, best rated,
 * in form), so the manager gets a scouting angle rather than a bare name.
 */
data class ScoutedPlayer(
    val playerId: Long,
    val name: String,
    val position: Position,
    val goals: Int = 0,
    val assists: Int = 0,
    val averageRating: Double = 0.0,
    val form: Double = 0.0,
    val overall: Int = 0,
    val reason: String = ""
)

/**
 * A compact opposition dossier for the next fixture. Everything is derived from
 * the live career — current table position, season goals, recent results and
 * availability — so it can never go stale the way a cached preview would.
 */
data class ScoutingReport(
    val opponentName: String,
    val opponentFormation: String,
    val leaguePosition: Int,
    val points: Int,
    val played: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    /** Season goals-per-game, the headline attacking threat. */
    val goalsPerGame: Double,
    /** 0..5 recent results, oldest first, from the opponent's perspective. */
    val recentForm: List<Int>,
    val keyPlayer: ScoutedPlayer?,
    val topScorer: ScoutedPlayer?,
    val playmaker: ScoutedPlayer?,
    val unavailableCount: Int,
    /** A one-line threat assessment the UI can show verbatim. */
    val threatSummary: String,
    /** The competition the fixture belongs to, for the header. */
    val competition: CompetitionType,
    /** How the last meeting between the two clubs finished, if there is one. */
    val lastMeeting: String?
)

/**
 * Builds the opposition dossier shown before kick-off. Pure and read-only: it
 * never mutates the career, so it is safe to call from any thread and cheap
 * enough to recompute whenever the match-day screen opens.
 */
object ScoutingEngine {

    fun report(career: Career, opponentId: Long, competition: CompetitionType): ScoutingReport {
        val opponent = career.club(opponentId)
        val squad = career.squadOf(opponentId)
        val leagueId = opponent?.leagueId ?: career.userLeagueId
        val row = career.sortedTable(leagueId).firstOrNull { it.clubId == opponentId }
        val played = row?.played ?: 0
        val gf = row?.goalsFor ?: 0
        val ga = row?.goalsAgainst ?: 0
        val position = career.positionOf(opponentId, leagueId)

        val goalsPerGame = if (played == 0) 0.0 else gf.toDouble() / played

        val form = career.recentForm(opponentId, count = 5).map { match ->
            val scored = match.goalsFor(opponentId)
            val conceded = match.goalsAgainst(opponentId)
            when {
                scored > conceded -> 2
                scored == conceded -> 1
                else -> 0
            }
        }

        val keyPlayer = bestKeyPlayer(squad)
        val topScorer = squad
            .filter { it.seasonStats.goals > 0 }
            .maxByOrNull { it.seasonStats.goals }
            ?.let { scouted(it, "Leading scorer") }
        val playmaker = squad
            .filter { it.seasonStats.assists > 0 }
            .maxByOrNull { it.seasonStats.assists }
            ?.let { scouted(it, "Chief creator") }

        val unavailable = squad.count { !it.isAvailable }

        return ScoutingReport(
            opponentName = opponent?.name ?: "Opponent",
            opponentFormation = SeasonEngine.tacticsFor(career, opponentId, career.matchdayIndex + 1).formation.name,
            leaguePosition = position,
            points = row?.points ?: 0,
            played = played,
            goalsFor = gf,
            goalsAgainst = ga,
            goalsPerGame = goalsPerGame,
            recentForm = form,
            keyPlayer = keyPlayer,
            topScorer = topScorer,
            playmaker = playmaker,
            unavailableCount = unavailable,
            threatSummary = threatSummary(position, played, gf, ga, goalsPerGame, form),
            competition = competition,
            lastMeeting = lastMeeting(career, opponentId)
        )
    }

    /** The single player the manager should be most worried about. */
    private fun bestKeyPlayer(squad: List<Player>): ScoutedPlayer? {
        if (squad.isEmpty()) return null
        val scorer = squad.maxByOrNull { it.seasonStats.goals }?.takeIf { it.seasonStats.goals > 0 }
        val rated = squad
            .filter { it.seasonStats.ratedMatches >= 3 }
            .maxByOrNull { it.seasonStats.averageRating }
        // Prefer a hot scorer; otherwise fall back to the best-rated regular.
        val chosen = scorer ?: rated ?: squad.maxByOrNull { it.overall }
        return chosen?.let {
            val reason = when {
                it.seasonStats.goals >= 5 -> "In red-hot scoring form"
                it.seasonStats.averageRating >= 7.3 -> "Their standout performer"
                it.form >= 7.0 -> "Currently in excellent form"
                else -> "Their danger man"
            }
            scouted(it, reason)
        }
    }

    private fun scouted(player: Player, reason: String): ScoutedPlayer = ScoutedPlayer(
        playerId = player.id,
        name = player.name,
        position = player.position,
        goals = player.seasonStats.goals,
        assists = player.seasonStats.assists,
        averageRating = player.seasonStats.averageRating,
        form = player.form,
        overall = player.overall,
        reason = reason
    )

    private fun threatSummary(
        position: Int,
        played: Int,
        gf: Int,
        ga: Int,
        goalsPerGame: Double,
        form: List<Int>
    ): String {
        if (played == 0) return "No league form yet this season — an unknown quantity."
        val attacking = when {
            goalsPerGame >= 2.0 -> "a prolific attack"
            goalsPerGame >= 1.4 -> "a steady attacking threat"
            goalsPerGame >= 1.0 -> "a modest attack"
            else -> "a blunt attack"
        }
        val defensive = when {
            ga.toDouble() / played <= 0.8 -> "a mean defence"
            ga.toDouble() / played <= 1.3 -> "a workmanlike defence"
            else -> "a leaky defence"
        }
        val recentWins = form.count { it == 2 }
        val formLine = when {
            recentWins >= 4 -> "They arrive in superb form"
            recentWins >= 3 -> "They arrive in good form"
            recentWins <= 1 -> "They arrive short of form"
            else -> "Their form is mixed"
        }
        val place = if (position in 1..20) "sitting ${ordinal(position)}" else "mid-table"
        return "$formLine, $place, with $attacking and $defensive."
    }

    /** How the most recent completed meeting between the clubs finished. */
    private fun lastMeeting(career: Career, opponentId: Long): String? {
        val previous = career.fixtures
            .filter { it.isPlayed && it.involves(career.userClubId) && it.involves(opponentId) }
            .maxByOrNull { it.matchday }
            ?: return null
        val userGoals = previous.goalsFor(career.userClubId)
        val theirGoals = previous.goalsAgainst(career.userClubId)
        val venue = if (previous.isHomeFor(career.userClubId)) "home" else "away"
        val outcome = when {
            userGoals > theirGoals -> "won"
            userGoals == theirGoals -> "drew"
            else -> "lost"
        }
        return "Last met $venue in matchday ${previous.matchday}: $outcome $userGoals–$theirGoals."
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
