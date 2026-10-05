package com.footymanager.simulator.domain.engine

import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.GameDate
import com.footymanager.simulator.domain.model.GamePhase
import kotlin.random.Random

/** A single completed match, as reported to the simulation animation. */
data class SimulatedFixtureResult(
    val matchId: Long,
    val date: GameDate,
    val competition: CompetitionType,
    val competitionLabel: String,
    val homeClubId: Long,
    val awayClubId: Long,
    val homeGoals: Int,
    val awayGoals: Int,
    val isUserMatch: Boolean
)

/** The outcome of advancing the world by exactly one matchday. */
data class WeekSimulation(
    val career: Career,
    val results: List<SimulatedFixtureResult>,
    /** 1 when a matchday was played, 0 when the season had nothing left. */
    val matchdaysAdvanced: Int
)

/**
 * Advances the whole football world to a future calendar date.
 *
 * Every scheduled match in every competition on the intervening matchdays is
 * played through the same [SeasonEngine] pipeline that a normal week uses, so
 * tables, player statistics, injuries, suspensions, finances and development all
 * move exactly as they would if the manager had played each week in turn.
 *
 * The work is exposed one matchday at a time through [simulateOneWeek] so the
 * caller can report genuine progress, reveal the user's results as they happen
 * and persist a consistent checkpoint after every week. [simulateThrough] keeps
 * the original one-shot behaviour for tests and headless callers.
 */
object SimulateToDateEngine {

    /** A hard cap so a malformed career can never spin forever. */
    private const val MAX_WEEKS = 80

    /**
     * Plays every fixture in the career's current matchday, applies the week's
     * housekeeping, then advances the calendar by one week.
     *
     * Fixtures dated after [target] are left untouched, so selecting a date
     * mid-week cannot pull the following weekend's fixtures forward.
     */
    fun simulateOneWeek(career: Career, target: GameDate, random: Random): WeekSimulation {
        val matchday = career.matchdayIndex + 1
        if (matchday > career.totalMatchdays()) return WeekSimulation(career, emptyList(), 0)

        var current = career
        var idCounter = current.idCounter
        val results = mutableListOf<SimulatedFixtureResult>()

        val pending = current.fixtures
            .filter { it.matchday == matchday && !it.isPlayed }
            .sortedWith(compareBy({ it.competition.ordinal }, { it.id }))

        for (match in pending) {
            // A fixture dated beyond the horizon belongs to a later week.
            if (match.date?.isAfter(target) == true) continue
            val userMatch = match.involves(current.userClubId)
            val (next, result) = SeasonEngine.simulateFixture(
                current, match, random, userMatch = userMatch
            )
            current = next
            results += SimulatedFixtureResult(
                matchId = match.id,
                date = match.date ?: current.date,
                competition = match.competition,
                competitionLabel = match.competitionLabel,
                homeClubId = match.homeClubId,
                awayClubId = match.awayClubId,
                homeGoals = result.homeGoals,
                awayGoals = result.awayGoals,
                isUserMatch = userMatch
            )
        }

        current = SeasonEngine.advanceWeek(current, random)
        current = FinanceEngine.applyFinancialPressure(current)
        current = ChampionsLeagueEngine.progress(current, random) { ++idCounter }
        current = CupEngine.progress(current) { ++idCounter }
        current = current.copy(idCounter = idCounter)

        return WeekSimulation(current, results, 1)
    }

    /**
     * Simulates every match up to and including [target], then returns the new
     * career. A convenience wrapper over [simulateOneWeek] for headless callers.
     */
    fun simulateThrough(
        career: Career,
        target: GameDate,
        random: Random,
        onResult: (SimulatedFixtureResult) -> Unit = {}
    ): Career {
        var current = career
        var guard = 0

        while (!current.date.isAfter(target) && guard < MAX_WEEKS) {
            guard++
            val week = simulateOneWeek(current, target, random)
            if (week.matchdaysAdvanced == 0) break
            current = week.career
            week.results.forEach(onResult)
        }

        if (current.matchdayIndex >= current.totalMatchdays()) {
            current = current.copy(phase = GamePhase.SEASON_ENDED)
        }

        // The calendar advances a whole week at a time, so the last step can land
        // a few days past the horizon. Clamp "today" back to the chosen date; the
        // matchday counter still reflects every matchday actually played.
        if (current.date.isAfter(target)) {
            current = current.copy(date = target)
        }
        return current
    }

    /**
     * The number of matches that would be played between the career's current date
     * and [target]. Used to tell the manager what they are about to simulate before
     * they confirm.
     */
    fun countMatches(career: Career, target: GameDate): Int =
        career.fixtures.count { !it.isPlayed && it.date != null && !it.date.isAfter(target) }

    /**
     * How many matchdays remain from the career's current state to the end of the
     * season, used as the progress bar's denominator.
     */
    fun remainingMatchdays(career: Career): Int =
        (career.totalMatchdays() - career.matchdayIndex).coerceAtLeast(0)
}
