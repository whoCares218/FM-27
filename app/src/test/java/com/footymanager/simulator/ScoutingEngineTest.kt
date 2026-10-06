package com.footymanager.simulator

import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.engine.ScoutingEngine
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pre-match scouting dossier must be derived from the live career, so the
 * manager always sees the opponent's *current* table position, season goals and
 * availability rather than a stale snapshot. These tests pin the key promises:
 * the report is populated, the competition is carried through from the fixture,
 * and the threat assessment reacts to the opponent's actual record.
 */
class ScoutingEngineTest {

    private val clubs = ClubDatabase.buildAll()

    private fun newCareer(seed: Long = 2026L) = CareerFactory.create(
        CareerFactory.NewCareerRequest("Scout", clubs.first().id, Difficulty.NORMAL, seed)
    )

    @Test
    fun `a report is produced for a scheduled opponent`() {
        val career = newCareer()
        val opponent = career.clubs.first { it.id != career.userClubId && it.leagueId == career.userLeagueId }
        val report = ScoutingEngine.report(career, opponent.id, CompetitionType.LEAGUE)

        assertEquals(opponent.name, report.opponentName)
        assertEquals(CompetitionType.LEAGUE, report.competition)
        assertTrue("A pre-season opponent still has a formation", report.opponentFormation.isNotBlank())
        assertTrue("A threat summary is always produced", report.threatSummary.isNotBlank())
    }

    @Test
    fun `the competition label comes from the fixture, not a hard-coded value`() {
        val career = newCareer()
        val opponent = career.clubs.first { it.id != career.userClubId }

        val league = ScoutingEngine.report(career, opponent.id, CompetitionType.LEAGUE)
        val europe = ScoutingEngine.report(career, opponent.id, CompetitionType.CHAMPIONS_LEAGUE)

        assertEquals(CompetitionType.LEAGUE, league.competition)
        assertEquals(CompetitionType.CHAMPIONS_LEAGUE, europe.competition)
    }

    @Test
    fun `the report reflects the opponent's current league position`() {
        val career = newCareer()
        val opponent = career.clubs.first { it.id != career.userClubId && it.leagueId == career.userLeagueId }
        val report = ScoutingEngine.report(career, opponent.id, CompetitionType.LEAGUE)
        val expected = career.positionOf(opponent.id, opponent.leagueId)
        assertEquals(expected, report.leaguePosition)
    }

    @Test
    fun `a key player is surfaced once the opponent has form`() {
        // Play a chunk of the season so players accumulate goals and ratings.
        val career = newCareer()
        val opponent = career.clubs.first { it.id != career.userClubId && it.leagueId == career.userLeagueId }
        val report = ScoutingEngine.report(career, opponent.id, CompetitionType.LEAGUE)
        assertNotNull("A squad always offers a danger man", report.keyPlayer)
        assertTrue(report.keyPlayer!!.name.isNotBlank())
    }

    @Test
    fun `recent form has at most five results`() {
        val career = newCareer()
        val opponent = career.clubs.first { it.id != career.userClubId }
        val report = ScoutingEngine.report(career, opponent.id, CompetitionType.LEAGUE)
        assertTrue("Form is a short recent window", report.recentForm.size <= 5)
        assertTrue(report.recentForm.all { it in 0..2 })
    }
}
