package com.footymanager.simulator

import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.engine.ChampionsLeagueEngine
import com.footymanager.simulator.domain.engine.SeasonEngine
import com.footymanager.simulator.domain.engine.SimulateToDateEngine
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Regression cover for the competition routing bug: a played result must reach
 * the table of *its own* competition, and the numbers written there must match
 * the scoreline actually produced — not a stale, unplayed copy of the fixture.
 *
 * The historical failure recorded every continental result as 0-0, so these
 * tests assert on goals (which a 0-0 shortcut cannot fake) as well as points.
 */
class CompetitionTableRegressionTest {

    private fun newCareer(seed: Long = 555L): Career =
        CareerFactory.create(
            CareerFactory.NewCareerRequest(
                "Table Tester", ClubDatabase.buildAll().first().id, Difficulty.NORMAL, seed
            )
        )

    /** Expected (played, won, drawn, lost, gf, ga, points) per club from results. */
    private data class Expected(
        var played: Int = 0,
        var won: Int = 0,
        var drawn: Int = 0,
        var lost: Int = 0,
        var gf: Int = 0,
        var ga: Int = 0
    ) {
        val points get() = won * 3 + drawn
    }

    private fun expectedFromResults(career: Career, competition: CompetitionType): Map<Long, Expected> {
        val expected = mutableMapOf<Long, Expected>()
        career.results.forEach { result ->
            val fixture = career.fixtures.firstOrNull { it.id == result.matchId } ?: return@forEach
            if (fixture.competition != competition || fixture.tieId != null) return@forEach
            val home = expected.getOrPut(fixture.homeClubId) { Expected() }
            val away = expected.getOrPut(fixture.awayClubId) { Expected() }
            home.played++; away.played++
            home.gf += result.homeGoals; home.ga += result.awayGoals
            away.gf += result.awayGoals; away.ga += result.homeGoals
            when {
                result.homeGoals > result.awayGoals -> { home.won++; away.lost++ }
                result.homeGoals < result.awayGoals -> { away.won++; home.lost++ }
                else -> { home.drawn++; away.drawn++ }
            }
        }
        return expected
    }

    @Test
    fun `a single continental result reaches its own table with the right goals`() {
        var career = newCareer()
        val match = career.fixtures.first {
            it.competition == CompetitionType.CHAMPIONS_LEAGUE && it.tieId == null && !it.isPlayed
        }
        val (updated, result) = SeasonEngine.simulateFixture(career, match, Random(3L), userMatch = false)
        career = updated

        val row = career.championsLeague.table.first { it.clubId == match.homeClubId }
        assertEquals("Home club should have played one game", 1, row.played)
        assertEquals("Goals for must match the scoreline", result.homeGoals, row.goalsFor)
        assertEquals("Goals against must match the scoreline", result.awayGoals, row.goalsAgainst)

        val awayRow = career.championsLeague.table.first { it.clubId == match.awayClubId }
        assertEquals(result.awayGoals, awayRow.goalsFor)
        assertEquals(result.homeGoals, awayRow.goalsAgainst)
    }

    @Test
    fun `a continental result does not touch the domestic table`() {
        var career = newCareer()
        val match = career.fixtures.first {
            it.competition == CompetitionType.CHAMPIONS_LEAGUE && it.tieId == null && !it.isPlayed
        }
        val before = career.table
        val (updated, _) = SeasonEngine.simulateFixture(career, match, Random(4L), userMatch = false)
        career = updated
        assertEquals("Domestic tables must be untouched by a UCL result", before, career.table)
    }

    @Test
    fun `every continental competition's table equals its own results per club`() {
        val career = newCareer()
        val updated = SimulateToDateEngine.simulateThrough(
            career, career.date.plusDays(200), Random(17L)
        )
        for (competition in listOf(
            CompetitionType.CHAMPIONS_LEAGUE,
            CompetitionType.EUROPA_LEAGUE,
            CompetitionType.CONFERENCE_LEAGUE
        )) {
            val expected = expectedFromResults(updated, competition)
            assertTrue("$competition should have played some matches", expected.isNotEmpty())
            val state = updated.europeanState(competition)
            for (row in state.table) {
                val exp = expected[row.clubId]
                if (exp == null) {
                    assertEquals("$competition club ${row.clubId} has no results", 0, row.played)
                    continue
                }
                assertEquals("$competition ${row.clubId} played", exp.played, row.played)
                assertEquals("$competition ${row.clubId} won", exp.won, row.won)
                assertEquals("$competition ${row.clubId} drawn", exp.drawn, row.drawn)
                assertEquals("$competition ${row.clubId} lost", exp.lost, row.lost)
                assertEquals("$competition ${row.clubId} goals for", exp.gf, row.goalsFor)
                assertEquals("$competition ${row.clubId} goals against", exp.ga, row.goalsAgainst)
                assertEquals("$competition ${row.clubId} points", exp.points, row.points)
            }
            val tableIds = state.table.map { it.clubId }.toSet()
            assertTrue(
                "$competition results reference clubs missing from the table",
                expected.keys.all { it in tableIds }
            )
        }
    }

    @Test
    fun `points in a continental table are consistent with wins and draws`() {
        val career = newCareer()
        val updated = SimulateToDateEngine.simulateThrough(
            career, career.date.plusDays(200), Random(23L)
        )
        for (competition in listOf(
            CompetitionType.CHAMPIONS_LEAGUE,
            CompetitionType.EUROPA_LEAGUE,
            CompetitionType.CONFERENCE_LEAGUE
        )) {
            updated.europeanState(competition).table.forEach { row ->
                assertEquals(
                    "$competition row ${row.clubId}: points must equal 3W + D",
                    row.won * 3 + row.drawn, row.points
                )
                assertEquals(
                    "$competition row ${row.clubId}: P must equal W + D + L",
                    row.won + row.drawn + row.lost, row.played
                )
                assertEquals(
                    "$competition row ${row.clubId}: GD must equal GF - GA",
                    row.goalsFor - row.goalsAgainst, row.goalDifference
                )
            }
        }
    }

    @Test
    fun `the live simulation and a direct fixture simulation agree on the table`() {
        val career = newCareer(seed = 321L)
        val match = career.fixtures.first {
            it.competition == CompetitionType.CHAMPIONS_LEAGUE && it.tieId == null && !it.isPlayed
        }
        val (direct, result) = SeasonEngine.simulateFixture(career, match, Random(9L), userMatch = false)
        val row = direct.championsLeague.table.first { it.clubId == match.homeClubId }
        assertEquals(result.homeGoals, row.goalsFor)
        assertEquals(result.awayGoals, row.goalsAgainst)
        assertTrue(
            "The result should be persisted on the fixture",
            direct.fixtures.first { it.id == match.id }.homeGoals == result.homeGoals
        )
    }

    @Test
    fun `ChampionsLeagueEngine applyLeagueResult ignores knockout fixtures`() {
        val career = newCareer()
        val state = career.championsLeague
        val knockout = career.fixtures.firstOrNull {
            it.competition == CompetitionType.CHAMPIONS_LEAGUE && it.tieId != null
        }
        if (knockout != null) {
            val after = ChampionsLeagueEngine.applyLeagueResult(state, knockout)
            assertEquals(
                "A knockout result must not touch the league-phase table",
                state.table, after.table
            )
        }
    }

    @Test
    fun `a domestic result reaches the domestic table and not a continental one`() {
        var career = newCareer()
        val beforeEurope = career.championsLeague.table
        val match = career.fixtures.first {
            it.competition == CompetitionType.LEAGUE && !it.isPlayed &&
                it.involves(career.userClubId)
        }
        val (updated, result) = SeasonEngine.simulateFixture(career, match, Random(5L), userMatch = true)
        career = updated
        val row = career.table[match.leagueId]!!.first { it.clubId == match.homeClubId }
        assertEquals(result.homeGoals, row.goalsFor)
        assertEquals(result.awayGoals, row.goalsAgainst)
        assertEquals(
            "A domestic result must not alter the Champions League table",
            beforeEurope, career.championsLeague.table
        )
    }
}
