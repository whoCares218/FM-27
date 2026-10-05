package com.footymanager.simulator.domain.data

import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.GameDate
import com.footymanager.simulator.domain.model.Match
import kotlin.random.Random

/**
 * Assigns a real calendar date to every fixture.
 *
 * The season is organised into weekly rounds. Each round has a domestic league
 * matchday; on a handful of designated rounds a continental matchday also falls in
 * the same week, which is what produces the familiar midweek European fixture
 * followed by a weekend league game.
 *
 * Every function here is anchored to a season start date. Season 1 begins on
 * [SEASON_START]; each later campaign begins one year later, so a fixture's date
 * is always relative to its own season rather than to a single global constant.
 * Passing a per-season start is what keeps the calendar correct across career
 * rollover: with a fixed anchor, a season-2 fixture would be dated in season 1 and
 * the whole schedule would sit in the past.
 */
object SeasonCalendar {

    /** The season starts on the second Saturday of August 2026. */
    val SEASON_START = GameDate(2026, 8, 8)

    /** A season's campaign is 52 weeks long (364 days), keeping the same weekday. */
    const val DAYS_PER_SEASON = 364

    /** The start date of the campaign with the given 1-based season number. */
    fun seasonStart(seasonNumber: Int): GameDate =
        SEASON_START.plusDays((seasonNumber.coerceAtLeast(1) - 1) * DAYS_PER_SEASON)

    /** Rounds that also carry a continental matchday (midweek, Tue/Wed). */
    private val uclRounds = setOf(2, 4, 6, 8, 10, 12, 14, 16)

    /** The domestic rounds on which each UCL league-phase matchday is played. */
    val uclRoundSchedule: List<Int> = uclRounds.toList().sorted()

    /** The Europa League shares the same midweek slots as the Champions League. */
    val europaRoundSchedule: List<Int> = uclRounds.toList().sorted()

    /** The Conference League plays six league-phase matchdays. */
    val conferenceRoundSchedule: List<Int> = uclRounds.toList().sorted().take(6)

    /** The midweek rounds carrying a given continental competition's league phase. */
    fun europeanRoundSchedule(competition: CompetitionType): List<Int> = when (competition) {
        CompetitionType.EUROPA_LEAGUE -> europaRoundSchedule
        CompetitionType.CONFERENCE_LEAGUE -> conferenceRoundSchedule
        else -> uclRoundSchedule
    }

    /**
     * The domestic round on which a continental competition's knockout stage
     * begins. The competitions are staggered so their brackets do not all fall in
     * the same weeks.
     */
    fun knockoutFirstMatchday(competition: CompetitionType): Int = when (competition) {
        CompetitionType.EUROPA_LEAGUE -> 19
        CompetitionType.CONFERENCE_LEAGUE -> 20
        else -> 18
    }

    /**
     * Applies dates to a full fixture list.
     *
     * @param fixtures every fixture for the season
     * @param random used only for the small home/away kick-off day variation
     * @param seasonStart the first day of the campaign the fixtures belong to
     */
    fun assignDates(
        fixtures: List<Match>,
        random: Random,
        seasonStart: GameDate = SEASON_START
    ): List<Match> {
        return fixtures.map { match ->
            val date = when (match.competition) {
                CompetitionType.LEAGUE -> leagueDate(match.matchday, seasonStart)
                CompetitionType.CHAMPIONS_LEAGUE, CompetitionType.EUROPA_LEAGUE,
                CompetitionType.CONFERENCE_LEAGUE ->
                    if (match.tieId != null) knockoutDate(match.matchday, seasonStart)
                    else europeanLeaguePhaseDate(
                        match.competition,
                        match.competitionRound.coerceAtLeast(1),
                        seasonStart
                    )
                CompetitionType.DOMESTIC_CUP -> cupDate(match.competitionRound.coerceAtLeast(1), seasonStart)
                CompetitionType.FRIENDLY -> leagueDate(match.matchday, seasonStart).plusDays(-3)
            }
            match.copy(date = date)
        }
    }

    /** Domestic league matchday N lands on a Saturday, one week apart. */
    fun leagueDate(matchday: Int, seasonStart: GameDate = SEASON_START): GameDate =
        seasonStart.plusDays((matchday - 1) * 7)

    /**
     * The Nth continental league-phase matchday sits midweek in the week of its
     * designated domestic round, so it never clashes with a league game.
     */
    fun uclLeaguePhaseDate(uclMatchday: Int, seasonStart: GameDate = SEASON_START): GameDate {
        val round = uclRounds.toList().sorted().getOrElse(uclMatchday - 1) {
            // Beyond the eight designated rounds, keep spacing sensible.
            uclRounds.max() + (uclMatchday - uclRounds.size) * 3
        }
        return seasonStart.plusDays((round - 1) * 7 - 3)
    }

    /** The league-phase date for any continental competition. */
    fun europeanLeaguePhaseDate(
        competition: CompetitionType,
        matchday: Int,
        seasonStart: GameDate = SEASON_START
    ): GameDate {
        val schedule = europeanRoundSchedule(competition)
        val round = schedule.getOrElse(matchday - 1) {
            schedule.max() + (matchday - schedule.size) * 3
        }
        return seasonStart.plusDays((round - 1) * 7 - 3)
    }

    /**
     * Knockout legs sit midweek in the week of their domestic round, so the
     * bracket completes before the league season finishes.
     */
    fun knockoutDate(matchday: Int, seasonStart: GameDate = SEASON_START): GameDate =
        leagueDate(matchday, seasonStart).plusDays(-3)

    /** The first domestic round that carries a cup tie. */
    private const val CUP_FIRST_ROUND = 5

    /** The domestic round a cup round is played in; rounds are a fortnight apart. */
    fun cupRoundMatchday(round: Int): Int = CUP_FIRST_ROUND + (round - 1) * 2

    /** Domestic cup rounds are midweek in the week of their designated round. */
    fun cupDate(round: Int, seasonStart: GameDate = SEASON_START): GameDate =
        leagueDate(cupRoundMatchday(round), seasonStart).plusDays(-3)

    /** The matchday a fixture's date falls in, used to advance the calendar. */
    fun roundOf(date: GameDate, seasonStart: GameDate = SEASON_START): Int {
        val days = daysBetween(seasonStart, date)
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
