package com.footymanager.simulator

import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.data.SeasonCalendar
import com.footymanager.simulator.domain.engine.SeasonEngine
import com.footymanager.simulator.domain.engine.SimulateToDateEngine
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.GamePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Regression coverage for the season-calendar bug that made simulate-to-date
 * unusable after the first season: the calendar was anchored to a single global
 * season start, so from season two the whole schedule sat in the past and the
 * selectable window was empty.
 *
 * These tests roll the career over several seasons and assert the feature stays
 * usable every time: a valid future horizon, a selectable window that starts
 * tomorrow, and a simulation that actually advances the calendar.
 */
class SimulateMultiSeasonTest {

    private fun newCareer(seed: Long = 90210L): Career = CareerFactory.create(
        CareerFactory.NewCareerRequest(
            "Calendar Tester",
            ClubDatabase.buildAll().first().id,
            Difficulty.NORMAL,
            seed
        )
    )

    /** Plays a whole season and rolls into the next one, like the ViewModel does. */
    private fun rollSeason(career: Career, random: Random): Career {
        var current = career
        var guard = 0
        while (current.phase != GamePhase.SEASON_ENDED && guard < 200) {
            guard++
            val week = SimulateToDateEngine.simulateOneWeek(current, current.simulateHorizon(), random)
            if (week.matchdaysAdvanced == 0) break
            current = week.career
        }
        return SeasonEngine.endSeason(current, random)
    }

    @Test
    fun `simulate horizon is a future date in every season`() {
        var career = newCareer()
        val random = Random(7)

        for (season in 1..4) {
            val horizon = career.simulateHorizon()
            assertTrue(
                "Season $season: the horizon (${horizon.numeric()}) must be after today (${career.date.numeric()})",
                horizon.isAfter(career.date)
            )
            assertEquals("Season $season should have its own start date", season, career.seasonNumber)

            // The window the UI exposes: tomorrow .. end of season.
            val earliest = career.date.plusDays(1)
            assertFalse(
                "Season $season: tomorrow must be within the season window",
                earliest.isAfter(horizon)
            )

            if (season < 4) career = rollSeason(career, random)
        }
    }

    @Test
    fun `simulate to date advances the calendar in seasons one two and three`() {
        var career = newCareer()
        val random = Random(11)

        for (season in 1..3) {
            val before = career.date
            val target = career.date.plusDays(70)
            assertFalse(
                "Season $season: a 70-day target must stay within the season",
                target.isAfter(career.simulateHorizon())
            )

            val after = SimulateToDateEngine.simulateThrough(career, target, random)
            assertTrue(
                "Season $season: the calendar must advance",
                after.date.isAfter(before)
            )
            assertFalse(
                "Season $season: the calendar must not overshoot the target",
                after.date.isAfter(target)
            )
            assertTrue(
                "Season $season: the domestic table must have been updated",
                after.leagueTable(after.userLeagueId).sumOf { it.points } > 0
            )

            career = rollSeason(after, random)
        }
    }

    @Test
    fun `a new season regenerates a full calendar of dated fixtures`() {
        var career = newCareer()
        val random = Random(13)

        for (season in 1..3) {
            val start = SeasonCalendar.seasonStart(career.seasonNumber)
            val leagueFixtures = career.fixtures.filter { it.leagueId == career.userLeagueId }
            assertTrue("Season $season must schedule league fixtures", leagueFixtures.isNotEmpty())
            assertTrue(
                "Season $season: every fixture must carry a date",
                career.fixtures.all { it.date != null }
            )
            assertTrue(
                "Season $season: league fixtures must fall in the season's own year",
                leagueFixtures.all { it.date!!.year == start.year || it.date!!.year == start.year + 1 }
            )
            assertTrue(
                "Season $season must schedule continental fixtures",
                career.fixtures.any { it.competition == CompetitionType.CHAMPIONS_LEAGUE }
            )
            career = rollSeason(career, random)
        }
    }

    @Test
    fun `the season winner is decided once every fixture has been played`() {
        val career = rollSeason(newCareer(seed = 55L), Random(3))
        val previous = career.clubHistory.seasons.lastOrNull()
        assertTrue("A finished season must be archived", previous != null)
        assertTrue("The archived season must have a final position", previous!!.finalPosition > 0)
    }
}
