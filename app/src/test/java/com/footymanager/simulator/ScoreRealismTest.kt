package com.footymanager.simulator

import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.data.PlayerGenerator
import com.footymanager.simulator.domain.data.SelectionHelper
import com.footymanager.simulator.domain.engine.MatchEngine
import com.footymanager.simulator.domain.engine.MatchTeamInput
import com.footymanager.simulator.domain.engine.ProgressiveMatchEngine
import com.footymanager.simulator.domain.engine.SerializableRandom
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.domain.model.Tactics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Guards the *shape* of the scoreline distribution rather than any single result.
 *
 * Real football produces a small number of blowouts and a large number of
 * tight, low-scoring games. These tests sample a realistic cross-section of the
 * league and fail if the engine drifts toward either extreme: a flood of
 * 7-0 scorelines, or a boring procession of 0-0 and 1-0.
 */
class ScoreRealismTest {

    private val clubs: List<Club> = ClubDatabase.buildAll()

    private fun input(club: Club, seed: Long): MatchTeamInput {
        val random = Random(seed)
        var id = seed * 10_000
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

    /** Simulates a full round-robin within each domestic league. */
    private fun sampleLeague(): List<Pair<Int, Int>> {
        val scores = mutableListOf<Pair<Int, Int>>()
        var seed = 0L
        for (league in League.domestic) {
            val leagueClubs = clubs.filter { it.leagueId == league.id }
            for (home in leagueClubs) {
                for (away in leagueClubs) {
                    if (home.id == away.id) continue
                    seed++
                    val result = MatchEngine.simulate(input(home, seed), input(away, seed + 1), Random(seed))
                    scores += result.homeGoals to result.awayGoals
                }
            }
        }
        return scores
    }

    @Test
    fun `goals per team stay in a realistic band`() {
        val scores = sampleLeague()
        val goalsPerTeam = scores.sumOf { it.first + it.second }.toDouble() / (scores.size * 2)
        assertTrue("Mean goals per team too low: $goalsPerTeam", goalsPerTeam > 0.9)
        assertTrue("Mean goals per team too high: $goalsPerTeam", goalsPerTeam < 1.9)
    }

    @Test
    fun `common scorelines dominate the distribution`() {
        val scores = sampleLeague()
        val total = scores.size.toDouble()
        // The everyday scorelines every league produces by the bucket load.
        val common = setOf(0 to 0, 1 to 0, 0 to 1, 1 to 1, 2 to 0, 0 to 2, 2 to 1, 1 to 2)
        val commonShare = scores.count { it in common } / total
        assertTrue("Common scorelines should be the majority (was $commonShare)", commonShare > 0.5)
    }

    @Test
    fun `very large margins are rare`() {
        val scores = sampleLeague()
        val total = scores.size.toDouble()
        val thrashings = scores.count { kotlin.math.abs(it.first - it.second) >= 5 }
        val share = thrashings / total
        assertTrue("Five-plus goal margins should be rare (was $share)", share < 0.03)
    }

    @Test
    fun `outlandish scorelines almost never happen`() {
        val scores = sampleLeague()
        val total = scores.size.toDouble()
        val absurd = scores.count { it.first >= 7 || it.second >= 7 || (it.first + it.second) >= 9 }
        val share = absurd / total
        val margins = scores.groupingBy { kotlin.math.abs(it.first - it.second) }.eachCount()
        val biggest = scores.maxByOrNull { it.first + it.second }
        println(
            "SCORE-DIST total=$total meanPerTeam=${scores.sumOf { it.first + it.second }.toDouble() / (total * 2)} " +
                "absurd=$absurd share=$share margins=$margins biggest=$biggest"
        )
        assertTrue("7+ goal hauls and 9+ goal games must be vanishingly rare (was $share)", share < 0.005)
    }

    @Test
    fun `high scoring games still occur sometimes`() {
        val scores = sampleLeague()
        val total = scores.size.toDouble()
        val fourPlus = scores.count { it.first + it.second >= 4 }
        val share = fourPlus / total
        assertTrue("Four-plus goal games should still happen (was $share)", share > 0.05)
        assertTrue("Four-plus goal games should not be the norm (was $share)", share < 0.45)
    }

    @Test
    fun `draws and narrow wins are the backbone of results`() {
        val scores = sampleLeague()
        val total = scores.size.toDouble()
        val draws = scores.count { it.first == it.second } / total
        assertTrue("Draws should be a meaningful share (was $draws)", draws > 0.12)
        assertTrue("Draws should not dominate (was $draws)", draws < 0.42)
    }

    @Test
    fun `the live engine produces a comparable distribution to the fast engine`() {
        val leagueClubs = clubs.filter { it.leagueId == League.PREMIER_LEAGUE.id }
        var seed = 0L
        var fastGoals = 0
        var liveGoals = 0
        var games = 0
        for (home in leagueClubs) {
            for (away in leagueClubs) {
                if (home.id == away.id) continue
                seed++
                games++
                val homeInput = input(home, seed)
                val awayInput = input(away, seed + 1)
                fastGoals += MatchEngine.simulate(homeInput, awayInput, Random(seed)).let { it.homeGoals + it.awayGoals }

                val engine = ProgressiveMatchEngine(
                    home = homeInput,
                    away = awayInput,
                    seedRandom = SerializableRandom(seed),
                    homeAdvantage = true
                )
                while (!engine.isFinished) {
                    if (engine.isHalfTime) engine.beginSecondHalf()
                    else engine.advance(45)
                }
                val (h, a) = engine.score()
                liveGoals += h + a
            }
        }
        val fastAvg = fastGoals.toDouble() / games
        val liveAvg = liveGoals.toDouble() / games
        assertTrue(
            "Live and fast engines should agree on the average total goals (fast=$fastAvg live=$liveAvg)",
            kotlin.math.abs(fastAvg - liveAvg) < 0.6
        )
    }

    @Test
    fun `a chasing side is more likely to score in the second half than a comfortable leader`() {
        // With a two-goal half-time deficit the model raises the chasing side's
        // second-half rate and lowers the leader's.
        val chasingRate = MatchEngine.secondHalfRate(baseXg = 2.0, ownFirstHalfGoals = 0, opponentFirstHalfGoals = 2)
        val leadingRate = MatchEngine.secondHalfRate(baseXg = 2.0, ownFirstHalfGoals = 2, opponentFirstHalfGoals = 0)
        assertTrue("A chasing side should attack more than a leader", chasingRate > leadingRate)
    }

    @Test
    fun `goal distribution is unchanged in aggregate for a big favourite`() {
        val strong = clubs.filter { it.leagueId == League.PREMIER_LEAGUE.id }.maxBy { it.reputation }
        val weak = clubs.filter { it.leagueId == League.PREMIER_LEAGUE.id }.minBy { it.reputation }

        var strongWins = 0
        var weakWins = 0
        var draws = 0
        repeat(600) { i ->
            val result = MatchEngine.simulate(input(strong, 9_000L + i), input(weak, 8_000L + i), Random(i.toLong()))
            when {
                result.homeGoals > result.awayGoals -> strongWins++
                result.homeGoals < result.awayGoals -> weakWins++
                else -> draws++
            }
        }
        assertTrue("A major favourite should win most games (was $strongWins)", strongWins > 300)
        assertTrue("But upsets must still be possible (was $weakWins)", weakWins > 10)
        assertEquals(600, strongWins + weakWins + draws)
    }
}
