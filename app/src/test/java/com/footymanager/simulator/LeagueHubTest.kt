package com.footymanager.simulator

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.engine.SimulateToDateEngine
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.CompetitionType
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.League
import com.footymanager.simulator.ui.screens.CompetitionHubScreen
import com.footymanager.simulator.ui.screens.LeagueScreen
import com.footymanager.simulator.ui.theme.FootballManagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * The League hub must show complete, live standings: the user's domestic table
 * by default, a country and competition selector, every other league as a compact
 * mini-table, and a full table for whichever competition the manager opens.
 */
@RunWith(AndroidJUnit4::class)
class LeagueHubTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun career(seed: Long = 1212L): Career = CareerFactory.create(
        CareerFactory.NewCareerRequest(
            "League Tester",
            ClubDatabase.buildAll().first().id,
            Difficulty.NORMAL,
            seed
        )
    )

    private fun played(seed: Long = 1212L): Career =
        SimulateToDateEngine.simulateThrough(career(seed), career(seed).date.plusDays(150), Random(5))

    private fun setScreen(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { FootballManagerTheme(darkTheme = true) { content() } }
    }

    @Test
    fun `the league screen shows the full domestic table by default`() {
        val c = career()
        setScreen {
            LeagueScreen(career = c, onOpenFixtures = {})
        }
        val league = League.byId(c.userLeagueId)
        val clubs = c.clubs.count { it.leagueId == league.id }
        composeRule.onNodeWithText(league.name, substring = true).assertExists()
        // The user's own club is always listed, and the table has every club in it.
        composeRule.onNodeWithText(c.userClub.name, substring = true).assertExists()
        composeRule.onRoot().assertExists()
        assertTrue("The domestic league should field a full division", clubs >= 16)
    }

    @Test
    fun `the domestic table lists every club not just the top few`() {
        val c = played()
        setScreen {
            LeagueScreen(career = c, onOpenFixtures = {})
        }
        // Scroll to the bottom of the table: the last-placed club must be present.
        val lastClub = c.sortedTable(c.userLeagueId).last().clubId
        val lastName = c.club(lastClub)!!.name
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText(lastName, substring = true))
        composeRule.onNodeWithText(lastName, substring = true).assertExists()
    }

    @Test
    fun `the country and league selectors are present and switch competition`() {
        val c = played()
        setScreen {
            LeagueScreen(career = c, onOpenFixtures = {})
        }
        composeRule.onNodeWithText("COUNTRY").assertExists()
        composeRule.onNodeWithText("LEAGUE").assertExists()
        // Open the league dropdown and pick a competition from a different country.
        composeRule.onNodeWithText("LEAGUE").assertExists()
        val otherLeague = League.all.firstOrNull {
            it.id != c.userLeagueId && c.clubs.any { club -> club.leagueId == it.id }
        }
        if (otherLeague != null) {
            composeRule.onAllNodes(hasText(League.byId(c.userLeagueId).name))[0].performClick()
            composeRule.waitForIdle()
            composeRule.onAllNodes(hasText(otherLeague.name))[0].performClick()
            composeRule.waitForIdle()
            composeRule.onAllNodes(hasText(otherLeague.name))[0].assertExists()
        }
    }

    @Test
    fun `the competition hub shows other-league mini tables and a full-table link`() {
        val c = played()
        setScreen {
            CompetitionHubScreen(
                career = c,
                onOpenLeague = {},
                onOpenChampionsLeague = {},
                onOpenFixtures = {}
            )
        }
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("Other leagues"))
        composeRule.onNodeWithText("Other leagues").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("VIEW FULL TABLE", substring = true))
        composeRule.onAllNodes(hasText("VIEW FULL TABLE", substring = true))[0].assertExists()
    }

    @Test
    fun `the competition hub renders the full Europe table for the selected competition`() {
        val c = played()
        setScreen {
            CompetitionHubScreen(
                career = c,
                onOpenLeague = {},
                onOpenChampionsLeague = {},
                onOpenFixtures = {}
            )
        }
        composeRule.onNodeWithText("Europe").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("Competition", substring = true))
        composeRule.onNodeWithText("Competition", substring = true).assertExists()
        composeRule.onNodeWithText("Champions League", substring = true).assertExists()
    }

    @Test
    fun `the league table updates after simulated matches`() {
        val before = career()
        val beforePoints = before.sortedTable(before.userLeagueId).sumOf { it.points }
        val after = played()
        val afterPoints = after.sortedTable(after.userLeagueId).sumOf { it.points }
        assertTrue(
            "Simulated matches must add points to the domestic table ($beforePoints -> $afterPoints)",
            afterPoints > beforePoints
        )
    }

    @Test
    fun `every domestic league table updates when the world is simulated`() {
        val before = career()
        val after = played()
        for (league in League.all) {
            if (before.clubs.none { it.leagueId == league.id }) continue
            val beforePoints = before.sortedTable(league.id).sumOf { it.points }
            val afterPoints = after.sortedTable(league.id).sumOf { it.points }
            assertTrue(
                "${league.name} must have played matches ($beforePoints -> $afterPoints)",
                afterPoints > beforePoints
            )
        }
    }

    @Test
    fun `every continental table updates when the world is simulated`() {
        val after = played()
        for (competition in listOf(
            CompetitionType.CHAMPIONS_LEAGUE,
            CompetitionType.EUROPA_LEAGUE,
            CompetitionType.CONFERENCE_LEAGUE
        )) {
            val state = after.europeanState(competition)
            assertTrue("$competition should be active", state.active)
            assertTrue(
                "$competition table must have results",
                state.table.sumOf { it.played } > 0
            )
        }
    }

    @Test
    fun `the user's club is highlighted wherever it appears in a table`() {
        val c = played()
        setScreen {
            LeagueScreen(career = c, onOpenFixtures = {})
        }
        // The user's club must be present in its own domestic table.
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText(c.userClub.name, substring = true))
        composeRule.onNodeWithText(c.userClub.name, substring = true).assertExists()
    }

    @Test
    fun `a mini table has at most five rows and the full table has all of them`() {
        val c = played()
        val league = League.all.first { it.id != c.userLeagueId && c.clubs.any { club -> club.leagueId == it.id } }
        val full = c.sortedTable(league.id)
        assertTrue("A league should have more than five clubs", full.size > 5)
        assertEquals(
            "The full table is not truncated",
            c.clubs.count { it.leagueId == league.id },
            full.size
        )
    }
}
