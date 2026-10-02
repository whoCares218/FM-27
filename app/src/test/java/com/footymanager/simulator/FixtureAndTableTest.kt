package com.footymanager.simulator

import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.data.FixtureGenerator
import com.footymanager.simulator.domain.model.GameDate
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.Match
import com.footymanager.simulator.domain.model.MatchStatus
import com.footymanager.simulator.domain.model.TableRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FixtureAndTableTest {

    private fun buildFixtures(): List<Match> {
        val clubs = ClubDatabase.buildAll().filter { it.leagueId == League.PREMIER_LEAGUE.id }
        var id = 0L
        return FixtureGenerator.generateLeagueFixtures(
            League.PREMIER_LEAGUE, clubs, Random(42)
        ) { ++id }
    }

    @Test
    fun `every club plays each opponent home and away`() {
        val clubs = ClubDatabase.buildAll().filter { it.leagueId == League.PREMIER_LEAGUE.id }
        val fixtures = buildFixtures()

        for (a in clubs) {
            for (b in clubs) {
                if (a.id == b.id) continue
                val homeMeetings = fixtures.count { it.homeClubId == a.id && it.awayClubId == b.id }
                val awayMeetings = fixtures.count { it.homeClubId == b.id && it.awayClubId == a.id }
                assertEquals("${a.name} should host ${b.name} once", 1, homeMeetings)
                assertEquals("${a.name} should visit ${b.name} once", 1, awayMeetings)
            }
        }
    }

    @Test
    fun `each club plays exactly once per matchday`() {
        val fixtures = buildFixtures()
        val byMatchday = fixtures.groupBy { it.matchday }

        for ((matchday, matches) in byMatchday) {
            val teams = matches.flatMap { listOf(it.homeClubId, it.awayClubId) }
            assertEquals(
                "Matchday $matchday should have no duplicate teams",
                teams.size, teams.toSet().size
            )
        }
    }

    @Test
    fun `matchday count matches a double round robin`() {
        val clubs = ClubDatabase.buildAll().filter { it.leagueId == League.PREMIER_LEAGUE.id }
        val fixtures = buildFixtures()
        val expectedMatchdays = (clubs.size - 1) * 2
        assertEquals(expectedMatchdays, fixtures.maxOf { it.matchday })
        assertEquals(clubs.size / 2, fixtures.count { it.matchday == 1 })
    }

    @Test
    fun `no club plays itself`() {
        assertTrue(buildFixtures().none { it.homeClubId == it.awayClubId })
    }

    @Test
    fun `table sorts by points then goal difference then goals scored`() {
        val rows = listOf(
            TableRow(clubId = 1, played = 10, won = 6, drawn = 2, lost = 2, goalsFor = 18, goalsAgainst = 10, points = 20),
            TableRow(clubId = 2, played = 10, won = 6, drawn = 2, lost = 2, goalsFor = 22, goalsAgainst = 12, points = 20),
            TableRow(clubId = 3, played = 10, won = 7, drawn = 1, lost = 2, goalsFor = 20, goalsAgainst = 12, points = 22),
            TableRow(clubId = 4, played = 10, won = 6, drawn = 2, lost = 2, goalsFor = 22, goalsAgainst = 14, points = 20)
        )
        val sorted = rows.sortedWith(TableRow.comparator)
        assertEquals(listOf(3L, 2L, 4L, 1L), sorted.map { it.clubId })
    }

    @Test
    fun `applying results accumulates the table correctly`() {
        var row = TableRow(clubId = 1)
        row = row.applyResult(3, 1)
        row = row.applyResult(2, 2)
        row = row.applyResult(0, 1)

        assertEquals(3, row.played)
        assertEquals(1, row.won)
        assertEquals(1, row.drawn)
        assertEquals(1, row.lost)
        assertEquals(5, row.goalsFor)
        assertEquals(4, row.goalsAgainst)
        assertEquals(1, row.goalDifference)
        assertEquals(4, row.points)
    }

    @Test
    fun `game date advances across month and year boundaries`() {
        val start = GameDate(2026, 8, 8)
        assertEquals(GameDate(2026, 8, 15), start.plusDays(7))
        assertEquals(GameDate(2026, 9, 1), start.plusDays(24))

        val december = GameDate(2026, 12, 28)
        assertEquals(GameDate(2027, 1, 4), december.plusDays(7))

        val feb = GameDate(2027, 2, 25)
        assertEquals(GameDate(2027, 3, 4), feb.plusDays(7))
    }

    @Test
    fun `game date handles leap years`() {
        assertEquals(29, GameDate.daysInMonth(2028, 2))
        assertEquals(28, GameDate.daysInMonth(2027, 2))
        assertEquals(GameDate(2028, 2, 29), GameDate(2028, 2, 22).plusDays(7))
    }

    @Test
    fun `season start date is a known weekday`() {
        // 8 August 2026 is a Saturday.
        val date = GameDate(2026, 8, 8)
        assertEquals("Sat", date.dayName)
    }

    @Test
    fun `played matches are excluded from the scheduled count`() {
        val fixtures = buildFixtures()
        val played = fixtures.mapIndexed { i, m ->
            if (i < 10) m.copy(status = MatchStatus.PLAYED, homeGoals = 1, awayGoals = 0) else m
        }
        assertEquals(10, played.count { it.isPlayed })
    }
}
