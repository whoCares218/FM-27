package com.footymanager.simulator

import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.data.PlayerGenerator
import com.footymanager.simulator.domain.data.SelectionHelper
import com.footymanager.simulator.domain.engine.MatchTeamInput
import com.footymanager.simulator.domain.engine.MatchRules
import com.footymanager.simulator.domain.engine.ProgressiveMatchEngine
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.MatchEventType
import com.footymanager.simulator.domain.model.Tactics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The progressive engine drives both Play Match and Quick Sim, so its contract
 * matters: it must stop at half time, keep the clock monotonic, keep statistics
 * internally consistent and respect a manager's half-time changes.
 */
class ProgressiveMatchEngineTest {

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

    private fun engine(seed: Long): ProgressiveMatchEngine = ProgressiveMatchEngine(
        home = buildInput(strongClub(), seed),
        away = buildInput(weakClub(), seed + 1),
        random = Random(seed),
        homeAdvantage = true,
        rules = MatchRules.LEAGUE
    )

    /** Drives a match to completion in fixed-size slices, honouring the interval. */
    private fun playMatch(e: ProgressiveMatchEngine, chunk: Int) {
        var guard = 0
        while (!e.isFinished && guard++ < 400) {
            e.advance(chunk)
            if (e.isHalfTime) e.beginSecondHalf()
        }
    }

    @Test
    fun `engine pauses at half time before the second half is played`() {
        val e = engine(3)
        // Play the entire first half (plus stoppage) in one go.
        e.advance(60)
        assertTrue("Engine must stop at half time", e.isHalfTime)
        assertFalse("Second half must not have been played yet", e.isFinished)

        val (hg, ag) = e.score()
        assertTrue("First-half score is non-negative", hg >= 0 && ag >= 0)
        assertTrue("Half-time event recorded", e.eventLog().any { it.type == MatchEventType.HALF_TIME })

        e.beginSecondHalf()
        e.advance(90)
        assertTrue("Engine finishes after the second half", e.isFinished)
        assertTrue("Full-time event recorded", e.eventLog().any { it.type == MatchEventType.FULL_TIME })
    }

    @Test
    fun `quick sim and play match produce identical results for the same seed`() {
        // The same seed must yield the same score regardless of how the advance()
        // calls are chunked, because the engine always steps one minute at a time.
        val minuteByMinute = engine(21)
        playMatch(minuteByMinute, 1)

        val chunked = engine(21)
        playMatch(chunked, 45)

        assertEquals("Scores must match across chunk sizes", minuteByMinute.score(), chunked.score())
        assertEquals(
            "Shots must match across chunk sizes",
            minuteByMinute.stats().first.shots,
            chunked.stats().first.shots
        )
    }

    @Test
    fun `event minutes are monotonic and within the match window`() {
        val e = engine(44)
        playMatch(e, 45)
        val minutes = e.eventLog().map { it.minute }
        assertEquals("Event log should be sorted", minutes.sorted(), minutes)
        assertTrue("No event before kick-off", minutes.min() >= 0)
        assertTrue("No event after the final whistle", minutes.max() <= 120)
    }

    @Test
    fun `statistics stay internally consistent`() {
        val e = engine(77)
        playMatch(e, 30)
        val (home, away) = e.stats()
        assertTrue(home.shotsOnTarget <= home.shots)
        assertTrue(away.shotsOnTarget <= away.shots)
        assertTrue(home.goals <= home.shotsOnTarget)
        assertTrue(away.goals <= away.shotsOnTarget)
        assertEquals(100, home.possession + away.possession)
        assertTrue("Pass accuracy plausible", home.passAccuracy in 55..96)
    }

    @Test
    fun `goal events match the scoreline`() {
        val e = engine(101)
        playMatch(e, 45)
        val (hg, ag) = e.score()
        val homeGoals = e.eventLog().count { it.type == MatchEventType.GOAL && it.clubId == e.home.clubId }
        val awayGoals = e.eventLog().count { it.type == MatchEventType.GOAL && it.clubId == e.away.clubId }
        assertEquals(hg, homeGoals)
        assertEquals(ag, awayGoals)
    }

    @Test
    fun `a single large advance still stops at half time`() {
        // Quick Sim may ask for a huge slice; the engine must never run past the
        // interval and silently play the second half without the manager.
        val e = engine(5)
        e.advance(90)
        assertTrue("A 90-minute request must stop at half time", e.isHalfTime)
        assertFalse(e.isFinished)
    }

    @Test
    fun `the engine is deterministic for a fixed seed`() {
        val a = engine(303)
        playMatch(a, 1)

        val b = engine(303)
        playMatch(b, 1)

        assertEquals(a.score(), b.score())
        assertEquals(a.stats().first.shots, b.stats().first.shots)
        assertEquals(a.eventLog().size, b.eventLog().size)
    }
}
