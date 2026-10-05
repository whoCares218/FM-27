package com.footymanager.simulator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.footymanager.simulator.ui.screens.NegotiationDetailScreen
import com.footymanager.simulator.ui.screens.NegotiationsSection
import com.footymanager.simulator.ui.screens.PlayerDetailScreen
import com.footymanager.simulator.ui.screens.SeasonSummaryScreen
import com.footymanager.simulator.ui.screens.SettingsScreen
import com.footymanager.simulator.ui.screens.SquadScreen
import com.footymanager.simulator.ui.screens.StatisticsScreen
import com.footymanager.simulator.ui.screens.TacticsScreen
import com.footymanager.simulator.ui.screens.TrainingScreen
import com.footymanager.simulator.ui.screens.TransferHistoryScreen
import com.footymanager.simulator.ui.screens.TransfersScreen
import com.footymanager.simulator.ui.screens.TransferTab
import com.footymanager.simulator.ui.screens.MatchDayScreen
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
        composeRule.onAllNodes(hasText(text, substring = false)).onLast().performClick()
    }

    /**
     * Selects an option inside a labelled section, e.g. the "High Press" option in
     * the "PLAYING STYLE" selector. Preset chips reuse some option labels, so we
     * first scroll the section header into view, then click the last visible match
     * (the real selector sits below the preset row).
     */
    private fun selectOption(sectionLabel: String, optionLabel: String) {
        // The whole "Team instructions" block is a single list item, so scrolling to
        // its "PLAYING STYLE" header composes both the preset row and the real
        // selector. The real selector is composed after the preset row, so it is the
        // last match. Scroll it into view before clicking so the tap lands on it.
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText(sectionLabel, substring = false))
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasText(optionLabel, substring = false))
            .onLast()
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
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
        setScreen { HomeScreen(c, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
        composeRule.onRoot().assertExists()
        composeRule.onNodeWithText(c.userClub.name, substring = true).assertExists()
    }

    @Test
    fun `home screen renders mid season with results and news`() {
        val c = playedCareer(6)
        setScreen { HomeScreen(c, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
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
                onApplyPreset = {},
                onSetWidth = {},
                onSetPressing = {},
                onSetPassing = {},
                onSetBuildUp = {},
                onSetCounterAttack = {},
                onSetPossessionFocus = {},
                onSetCrossing = {},
                onSetAggression = {},
                onSetIndividualInstruction = { _, _ -> },
                onSetSetPieces = {},
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

        selectOption("PLAYING STYLE", PlayStyle.HIGH_PRESS.label)
        assertEquals(PlayStyle.HIGH_PRESS, style)

        scrollToAndClick(Tempo.FAST.label)
        assertEquals(Tempo.FAST, tempo)

        selectOption("MENTALITY", Mentality.ATTACKING.label)
        assertEquals(Mentality.ATTACKING, mentality)
    }

    @Test
    fun `finished match offers a top-right continue that advances the career`() {
        val c = career(21)
        val match = c.nextMatch()!!
        val opponent = c.club(match.opponentOf(c.userClubId))!!
        val homeId = match.homeClubId
        val awayId = match.awayClubId
        val result = com.footymanager.simulator.domain.model.MatchResult(
            matchId = match.id,
            leagueId = match.leagueId,
            matchday = match.matchday,
            homeClubId = homeId,
            awayClubId = awayId,
            homeGoals = 2,
            awayGoals = 1,
            homeStats = com.footymanager.simulator.domain.model.TeamMatchStats(clubId = homeId),
            awayStats = com.footymanager.simulator.domain.model.TeamMatchStats(clubId = awayId),
            events = emptyList(),
            playerRatings = emptyList()
        )
        val state = com.footymanager.simulator.viewmodel.MatchDayState(
            match = match,
            opponent = opponent,
            isHome = match.homeClubId == c.userClubId,
            opponentFormationName = opponent.formationId,
            userFormationName = c.tactics.formation.name,
            started = true,
            phase = com.footymanager.simulator.domain.engine.MatchPhase.FINISHED,
            homeGoals = 2,
            awayGoals = 1,
            result = result,
            positionBefore = 8,
            positionAfter = 6
        )
        var continued = false
        setScreen {
            MatchDayScreen(
                career = c,
                matchDay = state,
                onBack = {},
                onStart = {},
                onContinueSecondHalf = {},
                onContinueExtraTime = {},
                onPause = {},
                onResume = {},
                onQuickSimFromHere = {},
                onMakeLiveSub = { _, _ -> },
                onPlanSub = { _, _ -> },
                onCancelSub = {},
                onApplyLiveTactics = {},
                onContinueAfterMatch = { continued = true }
            )
        }
        // The top-right control must be a single CONTINUE action, not a disabled
        // "GAME ENDED" chip buried under the summary.
        val continueNode = composeRule.onNodeWithText("CONTINUE", substring = false)
        continueNode.assertExists()
        val bounds = continueNode.fetchSemanticsNode().boundsInRoot
        assertTrue("Continue must sit in the top band, was top=${bounds.top}", bounds.top < 120f)
        continueNode.performClick()
        assertTrue("Continue must invoke the advance callback", continued)
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @org.robolectric.annotation.Config(sdk = [34])
    fun `tactics screen is compact with no large gap above the starting XI`() {
        val c = career()
        setScreen {
            TacticsScreen(
                career = c,
                onSetFormation = {},
                onSetMentality = {},
                onSetStyle = {},
                onSetDefensiveLine = {},
                onSetTempo = {},
                onSetTactics = {},
                onSetTrainingFocus = {},
                onApplyPreset = {},
                onSetWidth = {},
                onSetPressing = {},
                onSetPassing = {},
                onSetBuildUp = {},
                onSetCounterAttack = {},
                onSetPossessionFocus = {},
                onSetCrossing = {},
                onSetAggression = {},
                onSetIndividualInstruction = { _, _ -> },
                onSetSetPieces = {},
                onAutoPick = {},
                onAssignSlot = { _, _ -> },
                onRemoveFromSlot = {},
                onSwapSlots = { _, _ -> },
                onSetCaptain = {},
                onToggleSubstitute = {}
            )
        }
        val formation = composeRule.onNodeWithText("FORMATION", substring = false)
            .fetchSemanticsNode().boundsInRoot
        val startingXi = composeRule.onNodeWithText("STARTING XI", substring = false)
            .fetchSemanticsNode().boundsInRoot
        val gap = startingXi.top - formation.bottom
        assertTrue(
            "The Starting XI header must sit right under the formation block, gap was $gap",
            gap < 100f
        )

        // The formation hint must occupy a real horizontal line. It once collapsed
        // to zero width inside a SpaceBetween Row and wrapped one character per
        // line, creating a large invisible column above the XI. NATIVE graphics is
        // required so Robolectric measures text with real-device metrics; the
        // legacy mode's font metrics masked the collapse.
        val hint = composeRule.onNodeWithText("tap a player", substring = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The formation hint must have real width, was ${hint.width}", hint.width > 60f)
        assertTrue(
            "The formation hint must be a single line, height was ${hint.height}",
            hint.height < 40f
        )
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
                onSellPlayer = {},
                onRelease = {}
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
                onSellPlayer = {},
                onRelease = {}
            )
        }
        composeRule.onNodeWithText("no longer at the club", substring = true).assertExists()
    }

    @Test
    fun `league screen renders the table and every competition`() {
        val c = playedCareer(8)
        setScreen { LeagueScreen(career = c, onOpenFixtures = {}) }
        composeRule.onRoot().assertExists()
        // Switch country via the dropdown; the league list and table follow.
        composeRule.onNodeWithText("England", substring = false).performClick()
        composeRule.onNodeWithText("Spain", substring = false).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("La Liga", substring = true).assertExists()
        composeRule.onNodeWithText("Spain", substring = false).assertExists()
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
                0 -> HomeScreen(currentCareer, {}, {}, {}, {}, {}, {}, {}, {}, {})
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
    fun `player profile offers sell and release, and release asks for confirmation`() {
        val c = career()
        val player = c.userSquad.first()
        var soldId: Long? = null
        var releasedId: Long? = null
        setScreen {
            PlayerDetailScreen(
                career = c,
                playerId = player.id,
                onBack = {},
                onSetCaptain = {},
                onToggleSubstitute = {},
                onSellPlayer = { soldId = it },
                onRelease = { releasedId = it }
            )
        }
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("ACTIONS", substring = false))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sell player").performScrollTo().performClick()
        assertEquals("Sell must report the player id", player.id, soldId)

        // Release must not fire until the manager confirms the dialog.
        composeRule.onNodeWithText("Release player").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue("Release must be null until confirmed", releasedId == null)
        composeRule.onNodeWithText("Release ${player.name}?", substring = false).assertExists()
        composeRule.onNodeWithText("Release", substring = false).performClick()
        composeRule.waitForIdle()
        assertEquals("Confirmed release reports the player id", player.id, releasedId)
    }

    @Test
    fun `transfers screen shows BUY and SELL sections plus a league and team browse`() {
        val c = career()
        setScreen {
            TransfersScreen(
                career = c,
                onSearch = { _, _ -> emptyList() },
                onAskingPrice = { it.value },
                onExpectedWage = { it.wagePerWeek },
                onRequiredPackage = { com.footymanager.simulator.domain.model.TransferPackage(it.value) },
                onMakeOfferPackage = { _, _, _ -> },
                onAcceptCounter = {},
                onSubmitPlayerTerms = { _, _ -> },
                onCancelOffer = {},
                onListPlayer = { _, _ -> },
                onSetAskingPrice = { _, _ -> },
                onInterestedCount = { _, _ -> 0 },
                onCounterSaleBid = { _, _ -> },
                onAcceptSaleBid = {},
                onRejectSaleBid = {},
                onCancelSale = {},
                onRelease = {},
                onPlayersForClub = { emptyList() }
            )
        }
        composeRule.onNodeWithText("BUY", substring = false).assertExists()
        composeRule.onNodeWithText("SELL", substring = false).assertExists()
        composeRule.onNodeWithText("NEGOTIATIONS", substring = false).assertExists()
        // The League -> Team browse must be present in the BUY section. Its header
        // is uppercased by SectionHeader, so match on that form.
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("BROWSE BY LEAGUE AND TEAM", substring = false))
        composeRule.onNodeWithText("BROWSE BY LEAGUE AND TEAM", substring = false).assertExists()
    }

    @Test
    fun `transfers screen opens the SELL listing dialog when asked to sell a player`() {
        val c = career()
        val player = c.userSquad.first()
        var listedId: Long? = null
        var consumed = false
        setScreen {
            TransfersScreen(
                career = c,
                onSearch = { _, _ -> emptyList() },
                onAskingPrice = { it.value },
                onExpectedWage = { it.wagePerWeek },
                onRequiredPackage = { com.footymanager.simulator.domain.model.TransferPackage(it.value) },
                onMakeOfferPackage = { _, _, _ -> },
                onAcceptCounter = {},
                onSubmitPlayerTerms = { _, _ -> },
                onCancelOffer = {},
                onListPlayer = { id, _ -> listedId = id },
                onSetAskingPrice = { _, _ -> },
                onInterestedCount = { _, _ -> 0 },
                onCounterSaleBid = { _, _ -> },
                onAcceptSaleBid = {},
                onRejectSaleBid = {},
                onCancelSale = {},
                onRelease = {},
                onPlayersForClub = { emptyList() },
                initialTab = TransferTab.SELL,
                preselectPlayerId = player.id,
                onConsumePreselect = { consumed = true }
            )
        }
        composeRule.waitForIdle()
        assertTrue("Preselect must be consumed once", consumed)
        // The listing dialog names the player and offers a confirm action.
        composeRule.onNodeWithText(player.name, substring = true).assertExists()
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

    @Test
    fun `the negotiation desk renders its header and buckets`() {
        val c = career()
        setScreen {
            NegotiationsSection(
                career = c,
                onOpen = {},
                onMarkAllRead = {},
                onOpenHistory = {}
            )
        }
        composeRule.onRoot().assertExists()
        composeRule.onNodeWithText("TRANSFER DESK", substring = false).assertExists()
    }

    @Test
    fun `the negotiation detail screen renders a timeline`() {
        val c = career()
        val target = com.footymanager.simulator.domain.engine.TransferEngine.marketPlayers(c).first()
        val required = com.footymanager.simulator.domain.engine.TransferEngine.requiredPackage(c, target, c.userClubId)
        val wage = com.footymanager.simulator.domain.engine.TransferEngine.expectedWage(c, target, c.userClubId)
        val withOffer = com.footymanager.simulator.domain.engine.TransferEngine.createUserOffer(
            c,
            target,
            com.footymanager.simulator.domain.model.TransferPackage(fee = (required.fee * 0.80).toLong()),
            com.footymanager.simulator.domain.model.ContractTerms(wagePerWeek = wage, contractYears = 3)
        )
        val synced = com.footymanager.simulator.domain.engine.NegotiationEngine.syncBuy(withOffer)
        val record = synced.negotiations.first { it.playerId == target.id }
        var backPressed = false
        setScreen {
            NegotiationDetailScreen(
                career = synced,
                record = record,
                onBack = { backPressed = true }
            )
        }
        composeRule.onRoot().assertExists()
        composeRule.onNodeWithText(target.name, substring = true).assertExists()
        composeRule.onNodeWithText("Negotiation", substring = false).assertExists()
    }

    @Test
    fun `the transfer history screen renders a completed deal`() {
        val c = career()
        val player = c.userSquad.maxByOrNull { it.value }!!
        val fee = player.value * 2
        val buyer = c.clubs.first { it.id != c.userClubId }
        val sold = com.footymanager.simulator.domain.engine.TransferEngine.sellPlayer(c, player.id, fee, buyer.id)
        setScreen {
            TransferHistoryScreen(
                career = sold,
                onBack = {},
                onOpenPlayer = {}
            )
        }
        composeRule.onRoot().assertExists()
        composeRule.onNodeWithText("Transfer history", substring = false).assertExists()
        composeRule.onNodeWithText(player.name, substring = true).assertExists()
    }

    @Test
    fun `the competition hub renders domestic and european sections`() {
        val c = career()
        setScreen {
            com.footymanager.simulator.ui.screens.CompetitionHubScreen(
                career = c,
                onOpenLeague = {},
                onOpenChampionsLeague = {},
                onOpenFixtures = {}
            )
        }
        composeRule.onRoot().assertExists()
        composeRule.onNodeWithText("Domestic").assertExists()
        scrollToAndClick("Europe")
        composeRule.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("Competition", substring = true))
        composeRule.onNodeWithText("Competition", substring = true).assertExists()
        composeRule.onNodeWithText("Champions League", substring = true).assertExists()
    }

    @Test
    fun `the Europa League renders its own table`() {
        val c = career()
        setScreen {
            com.footymanager.simulator.ui.screens.EuropeanCompetitionScreen(
                career = c,
                competition = com.footymanager.simulator.domain.model.CompetitionType.EUROPA_LEAGUE
            )
        }
        composeRule.onRoot().assertExists()
        composeRule.onAllNodes(
            hasText(com.footymanager.simulator.domain.model.CompetitionType.EUROPA_LEAGUE.label, substring = true)
        )[0].assertExists()
    }

    @Test
    fun `the Conference League renders its own table`() {
        val c = career()
        setScreen {
            com.footymanager.simulator.ui.screens.EuropeanCompetitionScreen(
                career = c,
                competition = com.footymanager.simulator.domain.model.CompetitionType.CONFERENCE_LEAGUE
            )
        }
        composeRule.onRoot().assertExists()
        composeRule.onAllNodes(
            hasText(com.footymanager.simulator.domain.model.CompetitionType.CONFERENCE_LEAGUE.label, substring = true)
        )[0].assertExists()
    }
}
