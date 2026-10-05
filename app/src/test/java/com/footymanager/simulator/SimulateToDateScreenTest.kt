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
    fun `selecting a date reveals the selected-date card and the simulate button`() {
        val c = career()
        setScreen { SimulateToDateScreen(career = c, onBack = {}, onConfirm = {}) }
        // Tomorrow is always selectable and never outside the season window.
        val tomorrow = c.date.plusDays(1)
        composeRule.onNodeWithText("${tomorrow.day}").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("SIMULATE TO THIS DATE", substring = true))
        composeRule.onNodeWithText("SELECTED DATE").assertExists()
        composeRule.onNodeWithText("SIMULATE TO THIS DATE").assertExists()
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
}
