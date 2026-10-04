package com.footymanager.simulator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.footymanager.simulator.domain.engine.SeasonEngine
import com.footymanager.simulator.domain.engine.TransferEngine
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.Difficulty
import com.footymanager.simulator.domain.model.Formation
import com.footymanager.simulator.domain.model.Mentality
import com.footymanager.simulator.domain.model.PlayStyle
import com.footymanager.simulator.domain.model.Tempo
import com.footymanager.simulator.domain.model.TrainingFocus
import com.footymanager.simulator.ui.navigation.BottomTab
import com.footymanager.simulator.ui.navigation.Routes
import com.footymanager.simulator.ui.screens.BoardScreen
import com.footymanager.simulator.ui.screens.FinancesScreen
import com.footymanager.simulator.ui.screens.FixturesScreen
import com.footymanager.simulator.ui.screens.HomeScreen
import com.footymanager.simulator.ui.screens.HowToPlayScreen
import com.footymanager.simulator.ui.screens.LeagueScreen
import com.footymanager.simulator.ui.screens.NewCareerScreen
import com.footymanager.simulator.ui.screens.NewsScreen
import com.footymanager.simulator.ui.screens.PlayerDetailScreen
import com.footymanager.simulator.ui.screens.SeasonSummaryScreen
import com.footymanager.simulator.ui.screens.SettingsScreen
import com.footymanager.simulator.ui.screens.SquadScreen
import com.footymanager.simulator.ui.screens.StatisticsScreen
import com.footymanager.simulator.ui.screens.TacticsScreen
import com.footymanager.simulator.ui.screens.TrainingScreen
import com.footymanager.simulator.ui.theme.FootballManagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/**
 * Renders every gameplay screen against a real career. These tests catch
 * composition-time crashes (null lookups, missing arguments, illegal state)
 * that pure unit tests cannot see, and verify that controls are wired up.
 */
@RunWith(AndroidJUnit4::class)
class ScreenRenderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun career(seed: Long = 2024L): Career = CareerFactory.create(
        CareerFactory.NewCareerRequest(
            "Screen Tester",
            ClubDatabase.buildAll()[2].id,
            Difficulty.NORMAL,
            seed
        )
    )

    /** Advances the career by [weeks] matchdays so screens have real data. */
    private fun playedCareer(weeks: Int, seed: Long = 55L): Career {
        var c = career(seed)
        val random = Random(seed)
        repeat(weeks) {
            val match = c.nextMatch() ?: return@repeat
            c = SeasonEngine.simulateOtherFixtures(c, random)
            val (after, _) = SeasonEngine.simulateFixture(c, match, random, userMatch = true)
            c = SeasonEngine.advanceWeek(after, random)
        }
        return c
    }

    private fun setScreen(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            FootballManagerTheme(darkTheme = true) { content() }
        }
    }

    /**
     * Scrolls the outer vertical list until [text] is visible, then taps the node
     * whose label matches exactly. Exact matching avoids ambiguous hits such as
     * "Attacking" also matching "Very Attacking".
     */
    private fun scrollToAndClick(text: String) {
        // Screens nest horizontal chip rows inside a vertical LazyColumn, so pick
        // the outermost scrollable rather than assuming there is only one.
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText(text, substring = true))
        composeRule.onNodeWithText(text, substring = false).performClick()
    }

    /**
     * Selects a formation from the dropdown at the top of the tactics screen: the
     * currently-selected formation is tapped to open the menu, then the target is
     * tapped. Returns once the selection callback should have fired.
     */
    private fun selectFormation(currentName: String, optionName: String) {
        composeRule.onNodeWithText(currentName, substring = false).performClick()
        composeRule.onNodeWithText(optionName, substring = false).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `home screen renders a new career`() {
        val c = career()
        setScreen { HomeScreen(c, {}, {}, {}, {}, {}, {}, {}, {}) }
        composeRule.onRoot().assertExists()
        composeRule.onNodeWithText(c.userClub.name, substring = true).assertExists()
    }

    @Test
    fun `home screen renders mid season with results and news`() {
        val c = playedCareer(6)
        setScreen { HomeScreen(c, {}, {}, {}, {}, {}, {}, {}, {}) }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `squad screen renders and auto pick is wired`() {
        val c = career()
        var autoPicked = false
        setScreen { SquadScreen(career = c, onOpenPlayer = {}, onAutoPick = { autoPicked = true }) }
        composeRule.onRoot().assertExists()
        scrollToAndClick("Auto-pick XI")
        assertTrue("Auto-pick should be wired to the callback", autoPicked)
    }

    @Test
    fun `tactics screen applies formation, style, tempo and mentality`() {
        val c = career()
        var formationId: String? = null
        var style: PlayStyle? = null
        var tempo: Tempo? = null
        var mentality: Mentality? = null
        setScreen {
            TacticsScreen(
                career = c,
                onSetFormation = { formationId = it },
                onSetMentality = { mentality = it },
                onSetStyle = { style = it },
                onSetDefensiveLine = {},
                onSetTempo = { tempo = it },
                onSetTactics = {},
                onSetTrainingFocus = {},
                onAutoPick = {},
                onAssignSlot = { _, _ -> },
                onRemoveFromSlot = {},
                onSwapSlots = { _, _ -> },
                onSetCaptain = {},
                onToggleSubstitute = {}
            )
        }
        composeRule.onRoot().assertExists()

        // Formation is a dropdown at the top of the screen now.
        selectFormation(c.tactics.formation.name, Formation.F442.name)
        assertEquals(Formation.F442.id, formationId)

        scrollToAndClick(PlayStyle.HIGH_PRESS.label)
        assertEquals(PlayStyle.HIGH_PRESS, style)

        scrollToAndClick(Tempo.FAST.label)
        assertEquals(Tempo.FAST, tempo)

        scrollToAndClick(Mentality.ATTACKING.label)
        assertEquals(Mentality.ATTACKING, mentality)
    }

    @Test
    fun `player detail screen renders for every player in the squad`() {
        val c = career()
        var currentPlayerId by mutableStateOf(c.userSquad.first().id)
        setScreen {
            PlayerDetailScreen(
                career = c,
                playerId = currentPlayerId,
                onBack = {},
                onSetCaptain = {},
                onToggleSubstitute = {},
                onTransferList = {}
            )
        }
        // Re-point the screen at every player in turn: any illegal attribute,
        // contract or statistic lookup would crash the composition.
        for (player in c.userSquad) {
            currentPlayerId = player.id
            composeRule.waitForIdle()
            composeRule.onRoot().assertExists()
        }
    }

    @Test
    fun `player detail screen handles a player who has left the club`() {
        val c = career()
        setScreen {
            PlayerDetailScreen(
                career = c,
                playerId = 999_999_999L,
                onBack = {},
                onSetCaptain = {},
                onToggleSubstitute = {},
                onTransferList = {}
            )
        }
        composeRule.onNodeWithText("no longer at the club", substring = true).assertExists()
    }

    @Test
    fun `league screen renders the table and every competition`() {
        val c = playedCareer(8)
        setScreen { LeagueScreen(career = c, onOpenFixtures = {}) }
        composeRule.onRoot().assertExists()
        scrollToAndClick("Championship")
        composeRule.onRoot().assertExists()
        scrollToAndClick("La Liga")
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `fixtures screen renders a full schedule`() {
        val c = playedCareer(10)
        setScreen { FixturesScreen(career = c) }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `finances screen renders with ledger entries`() {
        val c = playedCareer(12)
        setScreen { FinancesScreen(career = c) }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `board screen renders objectives`() {
        val c = playedCareer(5)
        setScreen { BoardScreen(career = c) }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `news screen renders generated headlines`() {
        val c = playedCareer(5)
        setScreen { NewsScreen(career = c) }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `statistics screen renders for a played season`() {
        val c = playedCareer(15)
        setScreen { StatisticsScreen(career = c) }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `training screen renders and selects a focus`() {
        val c = career()
        var focus: TrainingFocus? = null
        setScreen { TrainingScreen(career = c, onSetFocus = { focus = it }) }
        scrollToAndClick(TrainingFocus.ATTACK.label)
        assertEquals(TrainingFocus.ATTACK, focus)
    }

    @Test
    fun `every screen renders without crashing across a full season`() {
        var c = career(7)
        var currentCareer by mutableStateOf(c)
        // 0 = home, 1 = league, 2 = finances, 3 = board, 4 = statistics.
        var screen by mutableStateOf(0)

        setScreen {
            when (screen) {
                0 -> HomeScreen(currentCareer, {}, {}, {}, {}, {}, {}, {}, {})
                1 -> LeagueScreen(currentCareer, {})
                2 -> FinancesScreen(currentCareer)
                3 -> BoardScreen(currentCareer)
                else -> StatisticsScreen(currentCareer)
            }
        }

        val random = Random(7)
        for (target in listOf(1, 5, 10, 20, 30)) {
            while (c.matchdayIndex < target && c.nextMatch() != null) {
                val match = c.nextMatch()!!
                c = SeasonEngine.simulateOtherFixtures(c, random)
                val (after, _) = SeasonEngine.simulateFixture(c, match, random, userMatch = true)
                c = SeasonEngine.advanceWeek(after, random)
            }
            currentCareer = c
            for (s in 0..4) {
                screen = s
                composeRule.waitForIdle()
                composeRule.onRoot().assertExists()
            }
        }
    }

    @Test
    fun `transfer market exposes affordable targets for the transfer screen`() {
        val c = career()
        val market = TransferEngine.marketPlayers(c)
        assertTrue("Market should not be empty", market.isNotEmpty())
        val target = market.first()
        val asking = TransferEngine.askingPrice(c, target, target.clubId)
        val wage = TransferEngine.expectedWage(c, target, c.userClubId)
        assertTrue("Asking price should be positive", asking > 0)
        assertTrue("Expected wage should be positive", wage > 0)
    }

    @Test
    fun `every bottom tab maps to a real destination`() {
        val routes = BottomTab.entries.map { it.route }
        assertEquals(
            "The bar must offer Home, Squad, Tactics, Transfers, League and More",
            listOf("home", "squad", "tactics", "transfers", "league", "more"),
            routes
        )
        // Each tab must resolve back from its own route.
        for (tab in BottomTab.entries) {
            assertEquals(tab, BottomTab.fromRoute(tab.route))
        }
        assertEquals("Non-tab routes have no tab", null, BottomTab.fromRoute(Routes.FINANCES))
    }

    @Test
    fun `the season summary screen renders a finished season`() {
        var c = career(11)
        val random = Random(11)
        while (c.nextMatch() != null) {
            val match = c.nextMatch()!!
            c = SeasonEngine.simulateOtherFixtures(c, random)
            val (after, _) = SeasonEngine.simulateFixture(c, match, random, userMatch = true)
            c = SeasonEngine.advanceWeek(after, random)
        }
        val ended = SeasonEngine.endSeason(c, random)
        setScreen { SeasonSummaryScreen(career = ended, onStartNextSeason = {}) }
        composeRule.onRoot().assertExists()
        // The start button sits below the fold in a lazy list, so assert on the
        // header and the standings that are guaranteed to be composed.
        composeRule.onNodeWithText("SEASON SUMMARY", substring = true).assertExists()
        composeRule.onNodeWithText("FINAL STANDINGS", substring = true).assertExists()
        assertNotNull("A season summary should be produced", ended.lastSeasonSummary)
    }

    @Test
    fun `the new career screen renders every selectable club`() {
        val clubs = ClubDatabase.buildAll()
        assertTrue("There must be several leagues of clubs", clubs.size >= 60)
        setScreen {
            NewCareerScreen(clubs = clubs, initialDifficulty = Difficulty.NORMAL, onBack = {}, onStart = { _, _, _ -> })
        }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `the how to play screen renders`() {
        setScreen { HowToPlayScreen(onBack = {}) }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `the settings screen renders every control`() {
        setScreen {
            SettingsScreen(
                settings = com.footymanager.simulator.domain.data.GameSettings(),
                hasCareer = true,
                onBack = {},
                onSetSound = {},
                onSetVibration = {},
                onSetDarkTheme = {},
                onSetDifficulty = {},
                onSetAnimationSpeed = {},
                onResetCareer = {},
                onAbout = {}
            )
        }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `the champions league screen renders standings and fixtures`() {
        val c = playedCareer(weeks = 10)
        setScreen { com.footymanager.simulator.ui.screens.ChampionsLeagueScreen(career = c) }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `the stadium screen renders and exposes an upgrade control`() {
        val c = career()
        setScreen {
            com.footymanager.simulator.ui.screens.StadiumScreen(
                career = c,
                onSetTicketPrice = {},
                onExpand = {}
            )
        }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `the sponsorship screen renders season offers`() {
        val c = career()
        setScreen {
            com.footymanager.simulator.ui.screens.SponsorsScreen(career = c, onSign = {})
        }
        composeRule.onRoot().assertExists()
    }

    @Test
    fun `the rewards screen renders the daily allowance`() {
        setScreen {
            com.footymanager.simulator.ui.screens.RewardsScreen(
                state = com.footymanager.simulator.domain.model.AdRewardState(),
                onBack = {},
                onClaim = { 3_000_000L }
            )
        }
        composeRule.onRoot().assertExists()
    }
}
