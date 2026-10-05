package com.footymanager.simulator

import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.engine.SimulateToDateEngine
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.GamePhase
import com.footymanager.simulator.domain.model.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Exercises the "simulate to date" pipeline end to end: the whole football world
 * must move forward together, every competition must receive its own results and
 * no fixture may be left behind or double-played.
 */
class SimulateToDateTest {

    private fun newCareer(seed: Long = 2026L): Career =
        CareerFactory.create(
            CareerFactory.NewCareerRequest("Sim Manager", ClubDatabase.buildAll().first().id, Difficulty.NORMAL, seed)
        )

    private fun play(career: Career, target: com.footymanager.simulator.domain.model.GameDate): Pair<Career, Int> {
        var count = 0
        val updated = SimulateToDateEngine.simulateThrough(career, target, Random(7L)) { count++ }
        return updated to count
    }

    @Test
    fun `simulating to a mid-season date advances the calendar`() {
        val career = newCareer()
        val target = career.date.plusDays(40)
        val (updated, count) = play(career, target)
        assertTrue("The calendar should have advanced", updated.date.isAfter(career.date))
        assertFalse("The date must not overshoot the horizon", updated.date.isAfter(target))
        assertTrue("Some matches should have been played", count > 0)
    }

    @Test
    fun `every played fixture carries a result and is marked played`() {
        val career = newCareer()
        val target = career.date.plusDays(90)
        val (updated, count) = play(career, target)
        val played = updated.fixtures.count { it.isPlayed }
        assertEquals("Reported results must equal persisted results", count, played)
        updated.fixtures.filter { it.isPlayed }.forEach { match ->
            assertEquals(MatchStatus.PLAYED, match.status)
            assertTrue("A played match must have a result", updated.results.any { it.matchId == match.id })
        }
    }

    @Test
    fun `no fixture dated after the target is played early`() {
        val career = newCareer()
        val target = career.date.plusDays(55)
        val (updated, _) = play(career, target)
        val early = updated.fixtures.filter { it.isPlayed && it.date?.isAfter(target) == true }
        assertTrue("No future fixture may be pulled forward: $early", early.isEmpty())
    }

    @Test
    fun `domestic and european tables both advance`() {
        val career = newCareer()
        val target = career.date.plusDays(150)
        val (updated, _) = play(career, target)

        val domestic = updated.leagueTable(updated.userLeagueId)
        assertTrue("Domestic table should have points on the board", domestic.sumOf { it.points } > 0)

        val continentalPlayed = listOf(
            CompetitionType.CHAMPIONS_LEAGUE,
            CompetitionType.EUROPA_LEAGUE,
            CompetitionType.CONFERENCE_LEAGUE
        ).sumOf { competition ->
            val state = updated.europeanState(competition)
            state.table.sumOf { it.played }
        }
        assertTrue("At least one continental competition should have played matches", continentalPlayed > 0)
    }

    @Test
    fun `a continental match updates only its own competition table`() {
        val career = newCareer()
        // Move to a date that certainly includes the first continental matchday.
        val target = career.date.plusDays(120)
        val (updated, _) = play(career, target)

        for (competition in listOf(
            CompetitionType.CHAMPIONS_LEAGUE,
            CompetitionType.EUROPA_LEAGUE,
            CompetitionType.CONFERENCE_LEAGUE
        )) {
            val state = updated.europeanState(competition)
            val continentalResults = updated.results.count { result ->
                val fixture = updated.fixtures.firstOrNull { it.id == result.matchId }
                fixture?.competition == competition && fixture.tieId == null
            }
            val tablePlayed = state.table.sumOf { it.played }
            // Every continental result is one played row across two clubs, so the
            // table's total played count must equal twice the number of results.
            assertEquals(
                "$competition table does not match its results",
                continentalResults * 2,
                tablePlayed
            )
        }
    }

    @Test
    fun `the user's club plays its own fixtures during the simulation`() {
        val career = newCareer()
        val target = career.date.plusDays(100)
        val (updated, _) = play(career, target)
        val userPlayed = updated.fixtures.count { it.isPlayed && it.involves(updated.userClubId) }
        assertTrue("The user's club should have played matches", userPlayed > 0)
    }

    @Test
    fun `finances and player statistics move during the simulation`() {
        val career = newCareer()
        val target = career.date.plusDays(100)
        val (updated, _) = play(career, target)

        val appearances = updated.players.sumOf { it.seasonStats.appearances }
        assertTrue("Player statistics should accumulate", appearances > 0)
        assertFalse(
            "Finances must not be frozen",
            updated.userClub.balance == career.userClub.balance
        )
    }

    @Test
    fun `a full season can be simulated to the end without corruption`() {
        val career = newCareer()
        val (updated, count) = play(career, career.simulateHorizon())
        assertEquals(GamePhase.SEASON_ENDED, updated.phase)
        assertTrue("The whole season should have been played", count > 400)
        // Every fixture in the season is complete, with no duplicates.
        assertTrue(updated.fixtures.all { it.isPlayed })
        assertEquals(
            "Fixture ids must be unique",
            updated.fixtures.size,
            updated.fixtures.map { it.id }.toSet().size
        )
        assertEquals(
            "Result match ids must be unique",
            updated.results.size,
            updated.results.map { it.matchId }.toSet().size
        )
    }

    @Test
    fun `simulating the same career twice is deterministic`() {
        val career = newCareer(seed = 99L)
        val target = career.date.plusDays(120)
        val (a, _) = play(career, target)
        val (b, _) = play(career, target)
        assertEquals(a.date, b.date)
        assertEquals(
            a.results.map { it.matchId to (it.homeGoals to it.awayGoals) },
            b.results.map { it.matchId to (it.homeGoals to it.awayGoals) }
        )
    }

    @Test
    fun `the horizon is the end of the season and is after today`() {
        val career = newCareer()
        val horizon = career.simulateHorizon()
        assertTrue(horizon.isAfter(career.date))
        assertNotNull(career.fixturesOnDate(career.date))
    }
}
