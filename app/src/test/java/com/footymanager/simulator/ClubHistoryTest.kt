package com.footymanager.simulator

import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.engine.SeasonEngine
import com.footymanager.simulator.domain.engine.SimulateToDateEngine
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.GamePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The club history is written at season turnover and read back by the club profile
 * screen. These tests drive a career through several seasons and assert the
 * archive accumulates correctly and never goes stale: season lines, all-time
 * player totals, wage totals, injury records, transfer records and trophies.
 */
class ClubHistoryTest {

    private fun newCareer(seed: Long = 8181L): Career = CareerFactory.create(
        CareerFactory.NewCareerRequest(
            "History Tester",
            ClubDatabase.buildAll().first().id,
            Difficulty.NORMAL,
            seed
        )
    )

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
    fun `each completed season is archived once`() {
        var career = newCareer()
        val random = Random(3)
        for (season in 1..3) {
            career = rollSeason(career, random)
            val archived = career.clubHistory.seasons.filter { it.seasonNumber == season }
            assertEquals("Season $season should be archived exactly once", 1, archived.size)
            val record = archived.first()
            assertTrue("Season $season must have a final position", record.finalPosition > 0)
            assertTrue("Season $season must record games played", record.played > 0)
            assertEquals(
                "Points must be consistent with wins and draws",
                record.won * 3 + record.drawn,
                record.points
            )
            assertEquals(
                "W+D+L must equal games played",
                record.won + record.drawn + record.lost,
                record.played
            )
        }
    }

    @Test
    fun `all-time player totals accumulate across seasons`() {
        var career = newCareer(seed = 2222L)
        val random = Random(5)
        career = rollSeason(career, random)
        career = rollSeason(career, random)

        val history = career.clubHistory
        assertTrue("Player records must exist", history.players.isNotEmpty())
        // At least one player should have appeared in both archived seasons.
        val multiSeason = history.players.values.filter { it.seasons >= 2 }
        assertTrue("Some players should span multiple seasons", multiSeason.isNotEmpty())
        for (player in history.players.values) {
            assertTrue("Appearances cannot be negative", player.appearances >= 0)
            assertEquals(
                "Starts plus substitute appearances must equal appearances",
                player.appearances,
                player.starts + player.substituteAppearances
            )
            if (player.appearances > 0) {
                assertTrue("A player with appearances must have minutes", player.minutesPlayed > 0)
            }
        }
    }

    @Test
    fun `total wages paid accumulates and is not just one season`() {
        var career = newCareer(seed = 3333L)
        val random = Random(9)
        career = rollSeason(career, random)
        val afterOne = career.clubHistory.players.values.maxOfOrNull { it.totalWagesPaid } ?: 0L
        career = rollSeason(career, random)
        val afterTwo = career.clubHistory.players.values.maxOfOrNull { it.totalWagesPaid } ?: 0L
        assertTrue("Wages must be recorded", afterOne > 0)
        assertTrue(
            "Wages must keep accumulating into a second season ($afterOne -> $afterTwo)",
            afterTwo > afterOne
        )
    }

    @Test
    fun `injury records accumulate days across the career`() {
        var career = newCareer(seed = 4444L)
        val random = Random(17)
        repeat(3) { career = rollSeason(career, random) }
        val injured = career.clubHistory.players.values.filter { it.injuries > 0 }
        // Injuries are probabilistic, but over three seasons across a squad of 25+
        // at least one should have been recorded.
        assertTrue("At least one player should have an injury record", injured.isNotEmpty())
        for (player in injured) {
            assertTrue("Injury days must be positive", player.injuryDays > 0)
            assertTrue(
                "The longest injury cannot exceed the total days missed",
                player.longestInjuryDays <= player.injuryDays
            )
        }
    }

    @Test
    fun `single-season record boards are populated after a season`() {
        var career = newCareer(seed = 5555L)
        val random = Random(21)
        career = rollSeason(career, random)
        val history = career.clubHistory
        assertTrue("Goals record must exist", history.recordGoalsInSeason != null)
        assertTrue("Points record must exist", history.recordHighestPoints != null)
        assertTrue("Appearances record must exist", history.recordAppearancesInSeason != null)
        assertTrue(
            "The goals record must have a positive value",
            (history.recordGoalsInSeason?.value ?: 0.0) > 0
        )
    }

    @Test
    fun `the club history survives a save and load round trip`() {
        var career = newCareer(seed = 6666L)
        val random = Random(23)
        career = rollSeason(career, random)

        val json = com.footymanager.simulator.domain.data.SaveCodec.encode(career)
        val reloaded = com.footymanager.simulator.domain.data.SaveCodec.decode(json)!!
        assertEquals(
            "Season history must survive serialization",
            career.clubHistory.seasons.size,
            reloaded.clubHistory.seasons.size
        )
        assertEquals(
            "Player career records must survive serialization",
            career.clubHistory.players.size,
            reloaded.clubHistory.players.size
        )
        assertEquals(
            "Wage totals must survive serialization",
            career.clubHistory.players.values.sumOf { it.totalWagesPaid },
            reloaded.clubHistory.players.values.sumOf { it.totalWagesPaid }
        )
        assertEquals(
            "Record boards must survive serialization",
            career.clubHistory.recordGoalsInSeason,
            reloaded.clubHistory.recordGoalsInSeason
        )
    }

    @Test
    fun `leaderboards are ordered by their metric`() {
        var career = newCareer(seed = 7777L)
        val random = Random(29)
        repeat(3) { career = rollSeason(career, random) }
        val history = career.clubHistory

        val scorers = history.topScorers()
        for (i in 1 until scorers.size) {
            assertTrue(
                "Top scorers must be in descending order",
                scorers[i - 1].goals >= scorers[i].goals
            )
        }
        val wages = history.topWages()
        for (i in 1 until wages.size) {
            assertTrue(
                "Wage board must be in descending order",
                wages[i - 1].totalWagesPaid >= wages[i].totalWagesPaid
            )
        }
        val injured = history.mostInjured()
        for (i in 1 until injured.size) {
            assertTrue(
                "Injury board must be in descending order",
                injured[i - 1].injuryDays >= injured[i].injuryDays
            )
        }
    }

    @Test
    fun `every competition table in the world stays internally consistent after simulation`() {
        val career = newCareer(seed = 8888L)
        val updated = SimulateToDateEngine.simulateThrough(
            career, career.date.plusDays(250), Random(31)
        )
        for ((leagueId, rows) in updated.table) {
            rows.forEach { row ->
                assertEquals(
                    "League $leagueId row ${row.clubId}: P must equal W+D+L",
                    row.played, row.won + row.drawn + row.lost
                )
                assertEquals(
                    "League $leagueId row ${row.clubId}: points must equal 3W+D",
                    row.won * 3 + row.drawn, row.points
                )
                assertEquals(
                    "League $leagueId row ${row.clubId}: GD must equal GF-GA",
                    row.goalsFor - row.goalsAgainst, row.goalDifference
                )
            }
        }
    }
}
