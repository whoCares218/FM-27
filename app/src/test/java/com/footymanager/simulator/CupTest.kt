package com.footymanager.simulator

import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.engine.CupEngine
import com.footymanager.simulator.domain.engine.SeasonEngine
import com.footymanager.simulator.domain.engine.SimulateToDateEngine
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.CupState
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.cupRoundLabel
import com.footymanager.simulator.domain.model.isEuropean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The domestic cup: a 32-club knockout that must be created, scheduled,
 * progressed round by round and settled with a winner, all through the normal
 * match engine so its ties carry the same statistics as any other fixture.
 */
class CupTest {

    private fun newCareer(seed: Long = 2026L, clubIndex: Int = 0): Career {
        val clubs = ClubDatabase.buildAll()
        return CareerFactory.create(
            CareerFactory.NewCareerRequest(
                "Cup Manager", clubs[clubIndex].id, Difficulty.NORMAL, seed
            )
        )
    }

    @Test
    fun `a new career creates a 32-club cup with sixteen first-round ties`() {
        val career = newCareer()
        val cup = career.cup
        assertTrue("The cup should be active", cup.active)
        assertEquals(32, cup.entrants.size)
        val firstRound = career.fixtures.filter {
            it.competition == CompetitionType.DOMESTIC_CUP && it.competitionRound == 1
        }
        assertEquals(16, firstRound.size)
        assertTrue("Cup ties are knockout", firstRound.all { it.isKnockout })
    }

    @Test
    fun `the user's club is always in the cup`() {
        // Even a lower-league club should get a cup run.
        val clubs = ClubDatabase.buildAll()
        val smallClubIndex = clubs.indexOfFirst { it.leagueId == "ENG2" }
        val career = newCareer(clubIndex = smallClubIndex)
        assertTrue(
            "The user's club must be an entrant",
            career.cup.entrants.contains(career.userClubId)
        )
    }

    @Test
    fun `every cup round is dated on its designated matchday`() {
        val career = newCareer()
        val cupFixtures = career.fixtures.filter { it.competition == CompetitionType.DOMESTIC_CUP }
        assertTrue(cupFixtures.isNotEmpty())
        cupFixtures.forEach { match ->
            val expectedMatchday = com.footymanager.simulator.domain.data.SeasonCalendar
                .cupRoundMatchday(match.competitionRound)
            assertEquals(expectedMatchday, match.matchday)
            assertNotNull("Cup fixtures must carry a date", match.date)
        }
    }

    @Test
    fun `a full season completes the cup with a single winner`() {
        val career = newCareer()
        val horizon = career.simulateHorizon()
        val updated = SimulateToDateEngine.simulateThrough(career, horizon, Random(11L))

        val cup = updated.cup
        assertNotNull("The cup must have a winner by season's end", cup.winnerClubId)
        assertTrue(
            "The winner must be one of the entrants",
            cup.entrants.contains(cup.winnerClubId) || updated.club(cup.winnerClubId!!) != null
        )

        // All 31 ties should have been played and the bracket fully resolved.
        val cupFixtures = updated.fixtures.filter { it.competition == CompetitionType.DOMESTIC_CUP }
        assertEquals("32 clubs means 31 ties", 31, cupFixtures.size)
        assertTrue("Every cup tie should be played", cupFixtures.all { it.isPlayed })
    }

    @Test
    fun `cup progression builds the next round from the winners`() {
        val career = newCareer()
        var current = career
        val random = Random(3L)
        // Play every first-round tie through the engine.
        val firstRound = current.fixtures.filter {
            it.competition == CompetitionType.DOMESTIC_CUP && it.competitionRound == 1
        }
        for (match in firstRound) {
            val (next, _) = SeasonEngine.simulateFixture(current, match, random, userMatch = false)
            current = next
        }
        var idc = current.idCounter
        current = CupEngine.progress(current) { ++idc }.copy(idCounter = idc)

        assertEquals("The cup should have advanced a round", 2, current.cup.round)
        assertEquals("Sixteen winners go into the next round", 16, current.cup.entrants.size)
        val secondRound = current.fixtures.filter {
            it.competition == CompetitionType.DOMESTIC_CUP && it.competitionRound == 2
        }
        assertEquals(8, secondRound.size)
        // Every second-round entrant must be a first-round winner.
        val winners = firstRound.mapNotNull { CupEngine.winnerOf(current.fixtures.first { f -> f.id == it.id }) }
        assertTrue(current.cup.entrants.containsAll(winners))
    }

    @Test
    fun `a level cup tie is decided by extra time or penalties`() {
        // Knockout rules allow extra time, so a tie can never remain level.
        val career = newCareer()
        val horizon = career.simulateHorizon()
        val updated = SimulateToDateEngine.simulateThrough(career, horizon, Random(21L))
        val played = updated.fixtures.filter {
            it.competition == CompetitionType.DOMESTIC_CUP && it.isPlayed
        }
        assertTrue(played.isNotEmpty())
        played.forEach { match ->
            val decided = match.homeGoals + match.homeGoalsExtraTime !=
                match.awayGoals + match.awayGoalsExtraTime ||
                (match.shootoutHome != null && match.shootoutHome != match.shootoutAway)
            assertTrue(
                "A cup tie must be decided (${match.homeGoals}-${match.awayGoals})",
                decided
            )
        }
    }

    @Test
    fun `simulate to date advances the cup`() {
        val career = newCareer()
        val target = career.date.plusDays(120)
        val updated = SimulateToDateEngine.simulateThrough(career, target, Random(5L))
        assertTrue("The cup should be well underway", updated.cup.round > 1)
        val playedCup = updated.fixtures.count {
            it.competition == CompetitionType.DOMESTIC_CUP && it.isPlayed
        }
        assertTrue("Several cup ties should have been played", playedCup >= 16)
    }

    @Test
    fun `the cup survives a save and load round trip`() {
        val career = newCareer()
        val target = career.date.plusDays(60)
        val simulated = SimulateToDateEngine.simulateThrough(career, target, Random(9L))
        val codec = com.footymanager.simulator.domain.data.SaveCodec
        val restored = codec.decode(codec.encode(simulated))
        assertNotNull(restored)
        assertEquals(simulated.cup, restored!!.cup)
    }

    @Test
    fun `cup round labels read correctly`() {
        assertEquals("Round of 32", cupRoundLabel(1))
        assertEquals("Round of 16", cupRoundLabel(2))
        assertEquals("Quarter-final", cupRoundLabel(3))
        assertEquals("Semi-final", cupRoundLabel(4))
        assertEquals("Final", cupRoundLabel(5))
    }

    @Test
    fun `simulate to date plays the cup alongside the leagues`() {
        val career = newCareer()
        val target = career.date.plusDays(160)
        val updated = SimulateToDateEngine.simulateThrough(career, target, Random(31L))

        // Cup rounds must not stall the rest of the world: domestic and
        // continental fixtures should be played on the same simulation pass.
        val cupPlayed = updated.fixtures.count {
            it.competition == CompetitionType.DOMESTIC_CUP && it.isPlayed
        }
        val leaguePlayed = updated.fixtures.count {
            it.competition == CompetitionType.LEAGUE && it.isPlayed
        }
        val europePlayed = updated.fixtures.count {
            it.competition.isEuropean && it.isPlayed
        }
        assertTrue("Cup ties should be played", cupPlayed >= 16)
        assertTrue("League matches should be played", leaguePlayed > 0)
        assertTrue("European matches should be played", europePlayed > 0)
    }

    @Test
    fun `the cup winner qualifies for the Europa League next season`() {
        val career = newCareer()
        val horizon = career.simulateHorizon()
        val completed = SimulateToDateEngine.simulateThrough(career, horizon, Random(41L))
        val winner = completed.cup.winnerClubId
        assertNotNull("There must be a cup winner", winner)

        // Roll into the next season; the winner should carry a UEL place unless
        // they have qualified for the Champions League instead.
        val next = SeasonEngine.endSeason(completed, Random(42L))
        val inUcl = next.championsLeague.participantIds.contains(winner)
        val inUel = next.europaLeague.participantIds.contains(winner)
        assertTrue(
            "The cup winner must be in Europe next season (UCL=$inUcl UEL=$inUel)",
            inUcl || inUel
        )
    }
}
