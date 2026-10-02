package com.footymanager.simulator

import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.data.PlayerGenerator
import com.footymanager.simulator.domain.data.SelectionHelper
import com.footymanager.simulator.domain.engine.MatchEngine
import com.footymanager.simulator.domain.engine.MatchTeamInput
import com.footymanager.simulator.domain.model.Attributes
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.MatchEventType
import com.footymanager.simulator.domain.model.Position
import com.footymanager.simulator.domain.model.Tactics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MatchEngineTest {

    private val clubs: List<Club> = ClubDatabase.buildAll()

    private fun strongClub(): Club = clubs.first { it.reputation >= 90 }
    private fun weakClub(): Club = clubs.filter { it.leagueId == "ENG1" }.minBy { it.reputation }

    private fun buildInput(club: Club, seed: Long): MatchTeamInput {
        val random = Random(seed)
        var id = seed * 1000
        val squad = PlayerGenerator.generateSquad(club, random) { ++id }
        val tactics = Tactics(formationId = Formation.F4231.id)
        val selection = SelectionHelper.autoPickBest(squad, Formation.byId(tactics.formationId))
        return MatchTeamInput(
            clubId = club.id,
            clubName = club.name,
            reputation = club.reputation,
            tactics = tactics,
            selection = selection,
            squadById = squad.associateBy { it.id }
        )
    }

    @Test
    fun `simulation produces internally consistent statistics`() {
        val home = buildInput(strongClub(), 1)
        val away = buildInput(weakClub(), 2)
        val result = MatchEngine.simulate(home, away, Random(7))

        assertTrue("Shots on target cannot exceed shots", result.homeStats.shotsOnTarget <= result.homeStats.shots)
        assertTrue("Shots on target cannot exceed shots", result.awayStats.shotsOnTarget <= result.awayStats.shots)
        assertTrue("Goals cannot exceed shots on target", result.homeStats.goals <= result.homeStats.shotsOnTarget)
        assertTrue("Goals cannot exceed shots on target", result.awayStats.goals <= result.awayStats.shotsOnTarget)
        assertTrue("Shots must be at least the goals scored", result.homeStats.shots >= result.homeStats.goals)
        assertEquals("Possession must sum to 100", 100, result.homeStats.possession + result.awayStats.possession)
        assertTrue("Possession in a plausible range", result.homeStats.possession in 25..75)
        assertTrue("Pass accuracy in a plausible range", result.homeStats.passAccuracy in 55..96)
        assertTrue("Corners are non-negative", result.homeStats.corners >= 0)
        assertTrue("Fouls are non-negative", result.homeStats.fouls >= 0)
    }

    @Test
    fun `goal events match the final score`() {
        val home = buildInput(strongClub(), 11)
        val away = buildInput(weakClub(), 12)
        val result = MatchEngine.simulate(home, away, Random(99))

        val homeGoalEvents = result.events.count { it.type == MatchEventType.GOAL && it.clubId == home.clubId }
        val awayGoalEvents = result.events.count { it.type == MatchEventType.GOAL && it.clubId == away.clubId }
        assertEquals(result.homeGoals, homeGoalEvents)
        assertEquals(result.awayGoals, awayGoalEvents)
    }

    @Test
    fun `timeline contains kick off half time and full time`() {
        val result = MatchEngine.simulate(buildInput(strongClub(), 3), buildInput(weakClub(), 4), Random(5))
        val types = result.events.map { it.type }
        assertTrue(types.contains(MatchEventType.KICK_OFF))
        assertTrue(types.contains(MatchEventType.HALF_TIME))
        assertTrue(types.contains(MatchEventType.FULL_TIME))
    }

    @Test
    fun `timeline is ordered by minute`() {
        val result = MatchEngine.simulate(buildInput(strongClub(), 6), buildInput(weakClub(), 7), Random(8))
        val minutes = result.events.map { it.minute }
        assertEquals(minutes.sorted(), minutes)
    }

    @Test
    fun `goal scorers are always named`() {
        val result = MatchEngine.simulate(buildInput(strongClub(), 21), buildInput(weakClub(), 22), Random(31))
        val goals = result.events.filter { it.type == MatchEventType.GOAL }
        assertTrue(goals.all { it.playerName.isNotBlank() })
    }

    @Test
    fun `a stronger side beats a much weaker side over many matches`() {
        val home = buildInput(strongClub(), 100)
        val away = buildInput(weakClub(), 200)

        var strongPoints = 0
        var weakPoints = 0
        var strongGoals = 0
        var weakGoals = 0

        repeat(400) { i ->
            // Alternate home and away so home advantage does not skew the sample.
            val strongAtHome = i % 2 == 0
            val result = if (strongAtHome) {
                MatchEngine.simulate(home, away, Random(i.toLong()))
            } else {
                MatchEngine.simulate(away, home, Random(i.toLong()))
            }
            val strongScored = if (strongAtHome) result.homeGoals else result.awayGoals
            val weakScored = if (strongAtHome) result.awayGoals else result.homeGoals

            strongGoals += strongScored
            weakGoals += weakScored
            when {
                strongScored > weakScored -> strongPoints += 3
                strongScored == weakScored -> { strongPoints += 1; weakPoints += 1 }
                else -> weakPoints += 3
            }
        }

        assertTrue("Stronger side should out-score the weaker side", strongGoals > weakGoals)
        assertTrue("Stronger side should take far more points", strongPoints > weakPoints * 2)
    }

    @Test
    fun `weaker sides can still win sometimes`() {
        val home = buildInput(strongClub(), 300)
        val away = buildInput(weakClub(), 400)

        var weakWins = 0
        repeat(600) { i ->
            val result = MatchEngine.simulate(away, home, Random(i.toLong() + 5000))
            if (result.homeGoals > result.awayGoals) weakWins++
        }
        // The weak side is at home in this sample; it should win a meaningful
        // minority of games but certainly not most of them.
        assertTrue("Weak side should win at least a few games (was $weakWins)", weakWins > 5)
        assertTrue("Weak side should not dominate (was $weakWins)", weakWins < 300)
    }

    @Test
    fun `home advantage is measurable`() {
        // Use two identical clubs so only home advantage differs.
        val club = strongClub()
        val home = buildInput(club, 900)
        val away = buildInput(club, 901)

        var homeGoals = 0
        var awayGoals = 0
        repeat(500) { i ->
            val result = MatchEngine.simulate(home, away, Random(i.toLong() + 20_000))
            homeGoals += result.homeGoals
            awayGoals += result.awayGoals
        }
        assertTrue("Home side should score more across a large sample", homeGoals > awayGoals)
    }

    @Test
    fun `same seed reproduces the same result`() {
        val home = buildInput(strongClub(), 55)
        val away = buildInput(weakClub(), 56)
        val first = MatchEngine.simulate(home, away, Random(1234))
        val second = MatchEngine.simulate(home, away, Random(1234))

        assertEquals(first.homeGoals, second.homeGoals)
        assertEquals(first.awayGoals, second.awayGoals)
        assertEquals(first.homeStats.shots, second.homeStats.shots)
        assertEquals(first.events.size, second.events.size)
    }

    @Test
    fun `every starter receives a rating`() {
        val home = buildInput(strongClub(), 61)
        val away = buildInput(weakClub(), 62)
        val result = MatchEngine.simulate(home, away, Random(63))

        assertEquals(11, result.ratings.count { home.selection.startingXi.map { s -> s.playerId }.contains(it.playerId) })
        assertEquals(11, result.ratings.count { away.selection.startingXi.map { s -> s.playerId }.contains(it.playerId) })
        assertTrue(result.ratings.all { it.rating in 3.5..10.0 })
        assertNotNull(result.playerOfTheMatchId)
    }

    @Test
    fun `attacking tactics produce more goals than defensive tactics on average`() {
        val club = strongClub()
        val base = buildInput(club, 700)
        val opponent = buildInput(weakClub(), 701)

        val attacking = base.copy(tactics = Tactics(mentality = com.footymanager.simulator.domain.model.Mentality.VERY_ATTACKING))
        val defensive = base.copy(tactics = Tactics(mentality = com.footymanager.simulator.domain.model.Mentality.VERY_DEFENSIVE))

        var attackGoals = 0
        var defendGoals = 0
        repeat(500) { i ->
            attackGoals += MatchEngine.simulate(attacking, opponent, Random(i.toLong() + 30_000)).homeGoals
            defendGoals += MatchEngine.simulate(defensive, opponent, Random(i.toLong() + 30_000)).homeGoals
        }
        assertTrue(
            "Attacking setup should out-score a defensive setup (attack=$attackGoals defend=$defendGoals)",
            attackGoals > defendGoals
        )
    }
}
