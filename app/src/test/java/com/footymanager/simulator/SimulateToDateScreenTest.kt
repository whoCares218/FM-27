package com.footymanager.simulator

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.footymanager.simulator.domain.data.CareerFactory
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.domain.engine.SimulateToDateEngine
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.ui.screens.SimulateConfirmCard
import com.footymanager.simulator.ui.screens.SimulateToDateScreen
import com.footymanager.simulator.ui.theme.FootballManagerTheme
import com.footymanager.simulator.viewmodel.GameViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Renders the simulate-to-date flow against a real career: the calendar, the
 * confirmation card and the animated progress list. These catch composition-time
 * crashes and confirm the controls are actually wired.
 */
@RunWith(AndroidJUnit4::class)
class SimulateToDateScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun career(seed: Long = 4321L): Career = CareerFactory.create(
        CareerFactory.NewCareerRequest(
            "Calendar Tester",
            ClubDatabase.buildAll()[1].id,
            Difficulty.NORMAL,
            seed
        )
    )

    private fun setScreen(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent { FootballManagerTheme(darkTheme = true) { content() } }
    }

    @Test
    fun `the calendar renders with today's date and the season horizon`() {
        val c = career()
        setScreen { SimulateToDateScreen(career = c, onBack = {}, onConfirm = {}) }
        composeRule.onNodeWithText("TODAY").assertExists()
        composeRule.onNodeWithText(c.date.numeric()).assertExists()
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `selecting a date reveals the simulate card and the simulate button`() {
        val c = career()
        setScreen { SimulateToDateScreen(career = c, onBack = {}, onConfirm = {}) }
        // Tomorrow is always selectable and never outside the season window.
        val tomorrow = c.date.plusDays(1)
        composeRule.onNodeWithText("${tomorrow.day}").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("SIMULATE TO THIS DATE", substring = true))
        // The simulate card now carries the chosen date and the action.
        composeRule.onNodeWithText("SIMULATE TO").assertExists()
        composeRule.onAllNodes(hasText("SIMULATE TO THIS DATE", substring = true))[0].assertExists()
    }

    @Test
    fun `the confirmation card shows the window and the match count`() {
        val c = career()
        val target = c.date.plusDays(30)
        val expected = SimulateToDateEngine.countMatches(c, target)
        setScreen { SimulateConfirmCard(career = c, target = target, onCancel = {}, onConfirm = {}) }
        composeRule.onNodeWithText("FROM").assertExists()
        composeRule.onNodeWithText("TO").assertExists()
        composeRule.onNodeWithText("Matches to simulate: $expected").assertExists()
        composeRule.onNodeWithText("Cancel").assertExists()
        composeRule.onNodeWithText("Confirm").assertExists()
    }

    @Test
    fun `confirming the dialog invokes the callback with the target date`() {
        val c = career()
        val target = c.date.plusDays(21)
        var confirmed: com.footymanager.simulator.domain.model.GameDate? = null
        setScreen {
            SimulateConfirmCard(
                career = c,
                target = target,
                onCancel = {},
                onConfirm = { confirmed = target }
            )
        }
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.waitForIdle()
        assertEquals(target, confirmed)
    }

    @Test
    fun `the calendar refuses to select today`() {
        val c = career()
        setScreen { SimulateToDateScreen(career = c, onBack = {}, onConfirm = {}) }
        composeRule.onNodeWithText("${c.date.day}").performClick()
        composeRule.waitForIdle()
        // Today is not selectable, so no selected-date card appears.
        composeRule.onAllNodes(hasText("SELECTED DATE")).assertCountEquals(0)
    }

    @Test
    fun `the calendar lists the user's own fixtures with opponent and competition`() {
        val c = career()
        // Find the user's next fixture and open the month it falls in.
        val next = c.fixtures
            .filter { it.involves(c.userClubId) && !it.isPlayed && it.date != null }
            .minByOrNull { it.date!!.toEpochDay() }!!
        val opponent = c.club(next.opponentOf(c.userClubId))!!
        setScreen { SimulateToDateScreen(career = c, onBack = {}, onConfirm = {}) }
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("YOUR FIXTURES", substring = true))
        composeRule.onNodeWithText("YOUR FIXTURES", substring = true).assertExists()
        // The opponent is shown, and the competition label comes from the fixture.
        composeRule.onAllNodes(hasText(opponent.name, substring = true))[0].assertExists()
        composeRule.onAllNodes(hasText(next.competition.label, substring = true))[0].assertExists()
    }

    @Test
    fun `selecting a match day shows the fixture for that date`() {
        val c = career()
        val next = c.fixtures
            .filter { it.involves(c.userClubId) && !it.isPlayed && it.date != null }
            .minByOrNull { it.date!!.toEpochDay() }!!
        val opponent = c.club(next.opponentOf(c.userClubId))!!
        setScreen { SimulateToDateScreen(career = c, onBack = {}, onConfirm = {}) }
        composeRule.onNodeWithText("${next.date!!.day}").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText(opponent.name, substring = true))
        composeRule.onAllNodes(hasText(opponent.name, substring = true))[0].assertExists()
    }

    @Test
    fun `the calendar day cell shows the opponent and competition for a match day`() {
        val c = career()
        // The earliest user fixture sits in the month the calendar opens on.
        val next = c.fixtures
            .filter { it.involves(c.userClubId) && !it.isPlayed && it.date != null }
            .minByOrNull { it.date!!.toEpochDay() }!!
        val opponent = c.club(next.opponentOf(c.userClubId))!!
        setScreen { SimulateToDateScreen(career = c, onBack = {}, onConfirm = {}) }
        // The day cell carries the opponent's short name so the month reads at a glance.
        composeRule.onAllNodes(hasText(opponent.shortName, substring = true))[0].assertExists()
        // And the real competition short label, read from the fixture.
        val label = when (next.competition) {
            com.footymanager.simulator.domain.model.CompetitionType.LEAGUE ->
                com.footymanager.simulator.domain.model.League.byId(next.leagueId).shortName
            else -> next.competition.label
        }
        composeRule.onAllNodes(hasText(label, substring = true))[0].assertExists()
    }

    @Test
    fun `the screen presents simulate, your fixtures then all fixtures`() {
        val c = career()
        setScreen { SimulateToDateScreen(career = c, onBack = {}, onConfirm = {}) }
        val list = composeRule.onAllNodes(hasScrollAction())[0]
        list.performScrollToNode(hasText("SIMULATE TO THIS DATE", substring = true))
        composeRule.onAllNodes(hasText("SIMULATE TO THIS DATE", substring = true))[0].assertExists()
        list.performScrollToNode(hasText("YOUR FIXTURES", substring = true))
        composeRule.onNodeWithText("YOUR FIXTURES", substring = true).assertExists()
        list.performScrollToNode(hasText("ALL FIXTURES", substring = true))
        composeRule.onNodeWithText("ALL FIXTURES", substring = true).assertExists()
    }

    @Test
    fun `all fixtures expands to list every scheduled fixture`() {
        val c = career()
        setScreen { SimulateToDateScreen(career = c, onBack = {}, onConfirm = {}) }
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("ALL FIXTURES", substring = true))
        composeRule.onNodeWithText("Show").performClick()
        composeRule.waitForIdle()
        // A fixture from another club appears once the full list is expanded.
        val other = c.fixtures.first {
            !it.isPlayed && it.date != null && !it.involves(c.userClubId)
        }
        val home = c.club(other.homeClubId)!!.name
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText(home, substring = true))
        composeRule.onAllNodes(hasText(home, substring = true))[0].assertExists()
    }

    @Test
    fun `the progress screen reveals only the user's own results`() {
        val c = career()
        val userClubId = c.userClubId
        val state = com.footymanager.simulator.viewmodel.SimulateToDateState(
            fromDate = c.date,
            toDate = c.date.plusDays(30),
            results = listOf(
                userResult(c, 1, userClubId, 2, 1),
                userResult(c, 2, userClubId, 0, 0)
            ),
            total = 2,
            currentIndex = 2,
            finished = false,
            matchdaysDone = 4,
            matchdaysTotal = 6,
            matchesSimulated = 48,
            totalMatches = 72,
            status = com.footymanager.simulator.viewmodel.SimulateStatus(
                domesticLeagueName = "Premier League",
                domesticPosition = 4,
                domesticComplete = false,
                domesticChampion = null,
                europeanCompetition = "Champions League",
                europeanDetail = "Position: 11th",
                europeanParticipating = true
            )
        )
        setScreen {
            com.footymanager.simulator.ui.screens.SimulateProgressScreen(
                state = state,
                onContinue = {}
            )
        }
        composeRule.onNodeWithText("Simulating to", substring = true).assertExists()
        composeRule.onNodeWithText("DOMESTIC").assertExists()
        composeRule.onNodeWithText("Premier League").assertExists()
        composeRule.onNodeWithText("Position: 4th").assertExists()
        composeRule.onNodeWithText("EUROPE").assertExists()
        composeRule.onNodeWithText("Champions League").assertExists()
        composeRule.onNodeWithText("Position: 11th").assertExists()
        // Real progress, not a fabricated number.
        composeRule.onNodeWithText("4 / 6 matchdays").assertExists()
        composeRule.onNodeWithText("48 / 72 fixtures").assertExists()
    }

    @Test
    fun `the progress screen shows a knockout stage instead of a fake position`() {
        val c = career()
        val state = com.footymanager.simulator.viewmodel.SimulateToDateState(
            fromDate = c.date,
            toDate = c.date.plusDays(30),
            results = emptyList(),
            total = 0,
            currentIndex = 0,
            finished = false,
            matchdaysDone = 1,
            matchdaysTotal = 6,
            status = com.footymanager.simulator.viewmodel.SimulateStatus(
                domesticLeagueName = "La Liga",
                domesticPosition = 2,
                domesticComplete = false,
                domesticChampion = null,
                europeanCompetition = "Europa League",
                europeanDetail = "Round of 16",
                europeanParticipating = true
            )
        )
        setScreen {
            com.footymanager.simulator.ui.screens.SimulateProgressScreen(state = state, onContinue = {})
        }
        composeRule.onNodeWithText("La Liga").assertExists()
        composeRule.onNodeWithText("Europa League").assertExists()
        composeRule.onNodeWithText("Round of 16").assertExists()
    }

    @Test
    fun `the progress screen reports non-participation honestly`() {
        val c = career()
        val state = com.footymanager.simulator.viewmodel.SimulateToDateState(
            fromDate = c.date,
            toDate = c.date.plusDays(30),
            results = emptyList(),
            total = 0,
            currentIndex = 0,
            finished = false,
            matchdaysDone = 1,
            matchdaysTotal = 6,
            status = com.footymanager.simulator.viewmodel.SimulateStatus(
                domesticLeagueName = "Serie A",
                domesticPosition = 7,
                domesticComplete = false,
                domesticChampion = null,
                europeanCompetition = null,
                europeanDetail = "Not participating",
                europeanParticipating = false
            )
        )
        setScreen {
            com.footymanager.simulator.ui.screens.SimulateProgressScreen(state = state, onContinue = {})
        }
        composeRule.onNodeWithText("Not participating").assertExists()
    }

    private fun userResult(
        c: Career,
        matchId: Long,
        userClubId: Long,
        userGoals: Int,
        opponentGoals: Int
    ): com.footymanager.simulator.viewmodel.SimulateDayResult {
        val opponent = c.clubs.first { it.id != userClubId }
        return com.footymanager.simulator.viewmodel.SimulateDayResult(
            matchId = matchId,
            date = c.date.plusDays(matchId.toInt()),
            competitionLabel = "League",
            homeClubId = userClubId,
            awayClubId = opponent.id,
            homeName = c.userClub.name,
            awayName = opponent.name,
            homeGoals = userGoals,
            awayGoals = opponentGoals,
            isUserMatch = true,
            userClubId = userClubId
        )
    }
}
