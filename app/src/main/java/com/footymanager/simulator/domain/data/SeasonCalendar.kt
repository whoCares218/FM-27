package com.footymanager.simulator.domain.data

import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.GameDate
import com.footymanager.simulator.domain.model.Match
import kotlin.random.Random

/**
 * Assigns a real calendar date to every fixture.
 *
 * The season is organised into weekly rounds. Each round has a domestic league
 * matchday; on a handful of designated rounds a Champions League matchday also
 * falls in the same week, which is what produces the familiar midweek European
 * fixture followed by a weekend league game.
 *
 * Dates are derived from the round number rather than stored randomly, so the
 * schedule is stable, readable and easy to test.
 */
object SeasonCalendar {

    /** The season starts on the second Saturday of August 2026. */
    val SEASON_START = GameDate(2026, 8, 8)

    /** Rounds that also carry a Champions League matchday (midweek, Tue/Wed). */
    private val uclRounds = setOf(2, 4, 6, 8, 10, 12, 14, 16)

    /** The domestic rounds on which each UCL league-phase matchday is played. */
    val uclRoundSchedule: List<Int> = uclRounds.toList().sorted()

    /**
     * Applies dates to a full fixture list.
     *
     * @param fixtures every fixture for the season
     * @param random used only for the small home/away kick-off day variation
     */
    fun assignDates(fixtures: List<Match>, random: Random): List<Match> {
        return fixtures.map { match ->
            val date = when (match.competition) {
                CompetitionType.LEAGUE -> leagueDate(match.matchday)
                CompetitionType.CHAMPIONS_LEAGUE ->
                    if (match.tieId != null) knockoutDate(match.matchday)
                    else uclLeaguePhaseDate(match.competitionRound.coerceAtLeast(1))
                CompetitionType.DOMESTIC_CUP -> cupDate(match.matchday)
                CompetitionType.FRIENDLY -> leagueDate(match.matchday).plusDays(-3)
            }
            match.copy(date = date)
        }
    }

    /** Domestic league matchday N lands on a Saturday, one week apart. */
    fun leagueDate(matchday: Int): GameDate =
        SEASON_START.plusDays((matchday - 1) * 7)

    /**
     * The Nth Champions League league-phase matchday sits midweek in the week of
     * its designated domestic round, so it never clashes with a league game.
     */
    fun uclLeaguePhaseDate(uclMatchday: Int): GameDate {
        val round = uclRounds.toList().sorted().getOrElse(uclMatchday - 1) {
            // Beyond the eight designated rounds, keep spacing sensible.
            uclRounds.max() + (uclMatchday - uclRounds.size) * 3
        }
        return SEASON_START.plusDays((round - 1) * 7 - 3)
    }

    /**
     * Knockout legs sit midweek in the week of their domestic round, so the
     * bracket completes before the league season finishes.
     */
    fun knockoutDate(matchday: Int): GameDate = leagueDate(matchday).plusDays(-3)

    /** Domestic cup rounds are midweek in the weeks between league games. */
    fun cupDate(round: Int): GameDate =
        SEASON_START.plusDays((round - 1) * 14 + 3)

    /** The matchday a fixture's date falls in, used to advance the calendar. */
    fun roundOf(date: GameDate): Int {
        val days = daysBetween(SEASON_START, date)
        return days / 7 + 1
    }

    private fun daysBetween(from: GameDate, to: GameDate): Int {
        var days = 0
        var cursor = from
        // The season spans well under two years, so a bounded loop is safe.
        while (days < 800 && (cursor.year != to.year || cursor.month != to.month || cursor.day != to.day)) {
            cursor = cursor.plusDays(1)
            days++
        }
        return days
    }
}
