package com.footymanager.simulator.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.footymanager.simulator.domain.data.ClubDatabase
import com.footymanager.simulator.ui.navigation.BottomTab
import com.footymanager.simulator.ui.navigation.Routes
import com.footymanager.simulator.ui.screens.AboutDialog
import com.footymanager.simulator.ui.screens.BoardScreen
import com.footymanager.simulator.ui.screens.ClubProfileScreen
import com.footymanager.simulator.ui.screens.ClubHistoryScreen
import com.footymanager.simulator.ui.screens.NegotiationDetailScreen
import com.footymanager.simulator.ui.screens.TransferHistoryScreen
import com.footymanager.simulator.ui.screens.CompetitionHubScreen
import com.footymanager.simulator.ui.screens.FinancesScreen
import com.footymanager.simulator.ui.screens.FixturesScreen
import com.footymanager.simulator.ui.screens.HomeScreen
import com.footymanager.simulator.ui.screens.HowToPlayScreen
import com.footymanager.simulator.ui.screens.LeagueScreen
import com.footymanager.simulator.ui.screens.MainMenuScreen
import com.footymanager.simulator.ui.screens.MatchDayScreen
import com.footymanager.simulator.ui.screens.MoreScreen
import com.footymanager.simulator.ui.screens.NewCareerScreen
import com.footymanager.simulator.ui.screens.NewsScreen
import com.footymanager.simulator.ui.screens.PlayerDetailScreen
import com.footymanager.simulator.ui.screens.RewardsScreen
import com.footymanager.simulator.ui.screens.SeasonSummaryScreen
import com.footymanager.simulator.ui.screens.SettingsScreen
import com.footymanager.simulator.ui.screens.SimulateProgressScreen
import com.footymanager.simulator.ui.screens.SimulateSummaryScreen
import com.footymanager.simulator.ui.screens.SimulateToDateScreen
import com.footymanager.simulator.ui.screens.SquadScreen
import com.footymanager.simulator.ui.screens.StatisticsScreen
import com.footymanager.simulator.ui.screens.TacticsScreen
import com.footymanager.simulator.ui.screens.TrainingScreen
import com.footymanager.simulator.ui.screens.TransfersScreen
import com.footymanager.simulator.ui.screens.TransferTab
import com.footymanager.simulator.ui.sound.SoundCue
import com.footymanager.simulator.ui.theme.FootballManagerTheme
import com.footymanager.simulator.viewmodel.GameViewModel
import com.footymanager.simulator.viewmodel.MatchMode

/** Root composable: owns the theme, the navigation graph and the bottom bar. */
@Composable
fun FootballManagerApp(viewModel: GameViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val career by viewModel.career.collectAsStateWithLifecycle()
    val hasSave by viewModel.hasSave.collectAsStateWithLifecycle()
    val matchDay by viewModel.matchDay.collectAsStateWithLifecycle()
    val simulateToDate by viewModel.simulateToDate.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    FootballManagerTheme(darkTheme = settings.darkTheme) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        var showAbout by remember { mutableStateOf(false) }

        // Keep the background music running while the app is in the foreground,
        // and stop it cleanly when the user leaves so it never plays in the
        // background or fights another app for audio focus. The player's setting
        // is re-asserted on every start, so turning music off stays off.
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner, settings.musicEnabled) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_START -> {
                        viewModel.music.enabled = settings.musicEnabled
                        viewModel.music.volume = settings.musicVolume
                        viewModel.startMusic()
                    }
                    androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.stopMusic()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            // The composable is currently resumed; make sure music is running.
            viewModel.music.enabled = settings.musicEnabled
            viewModel.music.volume = settings.musicVolume
            viewModel.startMusic()
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(message) {
            message?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearMessage()
            }
        }

        // Match-day audio: a whistle when the match is prepared, then a goal chime
        // once the result arrives with goals on the board.
        LaunchedEffect(matchDay?.match?.id) {
            if (matchDay != null) viewModel.playSound(SoundCue.WHISTLE)
        }
        LaunchedEffect(matchDay?.result) {
            val result = matchDay?.result ?: return@LaunchedEffect
            if (result.homeGoals + result.awayGoals > 0) viewModel.playSound(SoundCue.GOAL)
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val activeTab = BottomTab.fromRoute(currentRoute)
        val showBottomBar = activeTab != null && career != null

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        BottomTab.entries.forEach { tab ->
                            val selected = tab == activeTab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    viewModel.playSound(SoundCue.CLICK)
                                    if (!selected) {
                                        navController.navigate(tab.route) {
                                            popUpTo(Routes.HOME) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AppNavHost(
                    navController = navController,
                    viewModel = viewModel,
                    career = career,
                    matchDay = matchDay,
                    simulateToDate = simulateToDate,
                    hasSave = hasSave,
                    settings = settings,
                    isBusy = isBusy,
                    onShowAbout = { showAbout = true }
                )

                if (isBusy) {
                    LoadingOverlay()
                }
            }
        }

        if (showAbout) {
            AboutDialog(onDismiss = { showAbout = false })
        }
    }
}

/**
 * Rough "depth" of a route, used to decide the direction of the screen slide.
 * Tabs sit at depth 1 and every detail screen is deeper, so pushing into a
 * detail screen slides left and popping back slides right, matching the mental
 * model of moving forward and back through the app.
 */
private fun routeDepth(route: String?): Int {
    if (route == null) return 0
    return when {
        route == Routes.MAIN_MENU -> 0
        BottomTab.entries.any { it.route == route } -> 1
        else -> 2
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    viewModel: GameViewModel,
    career: com.footymanager.simulator.domain.model.Career?,
    matchDay: com.footymanager.simulator.viewmodel.MatchDayState?,
    simulateToDate: com.footymanager.simulator.viewmodel.SimulateToDateState?,
    hasSave: Boolean,
    settings: com.footymanager.simulator.domain.data.GameSettings,
    isBusy: Boolean,
    onShowAbout: () -> Unit
) {
    val clubs = remember { ClubDatabase.buildAll() }

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN_MENU,
        enterTransition = {
            val forward = routeDepth(targetState.destination.route) >= routeDepth(initialState.destination.route)
            slideIntoContainer(
                if (forward) AnimatedContentTransitionScope.SlideDirection.Left
                else AnimatedContentTransitionScope.SlideDirection.Right,
                tween(260)
            ) + fadeIn(tween(220))
        },
        exitTransition = {
            val forward = routeDepth(targetState.destination.route) >= routeDepth(initialState.destination.route)
            slideOutOfContainer(
                if (forward) AnimatedContentTransitionScope.SlideDirection.Left
                else AnimatedContentTransitionScope.SlideDirection.Right,
                tween(260)
            ) + fadeOut(tween(170))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                tween(260)
            ) + fadeIn(tween(220))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                tween(260)
            ) + fadeOut(tween(170))
        }
    ) {
        // ------------------------------------------------------ main menu
        composable(Routes.MAIN_MENU) {
            MainMenuScreen(
                hasSave = hasSave,
                savedCareer = career,
                onNewCareer = { navController.navigate(Routes.NEW_CAREER) },
                onContinue = {
                    viewModel.continueCareer()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.MAIN_MENU) { inclusive = true }
                    }
                },
                onLoadGame = {
                    viewModel.loadCareer()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.MAIN_MENU) { inclusive = true }
                    }
                },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onHowToPlay = { navController.navigate(Routes.HOW_TO_PLAY) }
            )
        }

        composable(Routes.NEW_CAREER) {
            NewCareerScreen(
                clubs = clubs,
                initialDifficulty = settings.difficulty,
                onBack = { navController.popBackStack() },
                onStart = { name, clubId, difficulty ->
                    viewModel.startNewCareer(name, clubId, difficulty)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.MAIN_MENU) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOW_TO_PLAY) {
            HowToPlayScreen(onBack = { navController.popBackStack() })
        }

        // ---------------------------------------------------------- home
        composable(Routes.HOME) {
            if (career == null) {
                EmptyCareerState(onReturn = {
                    navController.navigate(Routes.MAIN_MENU) {
                        popUpTo(Routes.MAIN_MENU) { inclusive = true }
                    }
                })
            } else {
                HomeScreen(
                    career = career,
                    onPlayMatch = {
                        if (viewModel.prepareNextMatch()) {
                            navController.navigate(Routes.MATCH_DAY)
                        }
                    },
                    onQuickSim = {
                        // Quick Sim runs the same live engine in compressed form and
                        // still pauses at half time for the manager.
                        if (viewModel.prepareNextMatch()) {
                            navController.navigate(Routes.MATCH_DAY)
                            viewModel.startMatch(MatchMode.QUICK)
                        }
                    },
                    onSimulateToDate = {
                        if (viewModel.hasActiveMatch()) {
                            viewModel.showMatchInProgressMessage()
                        } else {
                            navController.navigate(Routes.SIMULATE_TO_DATE)
                        }
                    },
                    onOpenSquad = { navController.navigate(Routes.SQUAD) },
                    onOpenLeague = { navController.navigate(Routes.LEAGUE) },
                    onOpenNews = { navController.navigate(Routes.NEWS) },
                    onOpenBoard = { navController.navigate(Routes.BOARD) },
                    onOpenSponsors = { navController.navigate(Routes.SPONSORS) },
                    onStartNextSeason = { navController.navigate(Routes.SEASON_SUMMARY) },
                    onOpenClubProfile = { navController.navigate(Routes.CLUB_HISTORY) }
                )
            }
        }

        // --------------------------------------------------------- squad
        composable(Routes.SQUAD) {
            if (career != null) {
                SquadScreen(
                    career = career,
                    onOpenPlayer = { id -> navController.navigate(Routes.playerDetail(id)) },
                    onAutoPick = { viewModel.autoPickSelection() }
                )
            }
        }

        composable(
            route = Routes.PLAYER_DETAIL,
            arguments = listOf(navArgument("playerId") { type = NavType.LongType })
        ) { entry ->
            val playerId = entry.arguments?.getLong("playerId") ?: return@composable
            if (career != null) {
                PlayerDetailScreen(
                    career = career,
                    playerId = playerId,
                    onBack = { navController.popBackStack() },
                    onSetCaptain = { viewModel.setCaptain(it) },
                    onToggleSubstitute = { viewModel.toggleSubstitute(it) },
                    onSellPlayer = { playerId ->
                        viewModel.requestSellPlayer(playerId)
                        navController.navigate(Routes.TRANSFERS) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                    onRelease = { viewModel.releasePlayer(it) }
                )
            }
        }

        composable(
            route = Routes.CLUB_PROFILE,
            arguments = listOf(navArgument("clubId") { type = NavType.LongType })
        ) { entry ->
            val clubId = entry.arguments?.getLong("clubId") ?: return@composable
            if (career != null) {
                ClubProfileScreen(
                    career = career,
                    clubId = clubId,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { id -> navController.navigate(Routes.playerDetail(id)) }
                )
            }
        }

        composable(Routes.CLUB_HISTORY) {
            if (career != null) {
                ClubHistoryScreen(
                    career = career,
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { id -> navController.navigate(Routes.playerDetail(id)) }
                )
            }
        }

        composable(
            route = Routes.NEGOTIATION_DETAIL,
            arguments = listOf(navArgument("negotiationId") { type = NavType.LongType })
        ) { entry ->
            val negotiationId = entry.arguments?.getLong("negotiationId") ?: return@composable
            val current = career ?: return@composable
            val record = viewModel.negotiationById(negotiationId)
            if (record != null) {
                NegotiationDetailScreen(
                    career = current,
                    record = record,
                    onBack = { navController.popBackStack() }
                )
            } else {
                navController.popBackStack()
            }
        }

        composable(Routes.TRANSFER_HISTORY) {
            val current = career ?: return@composable
            TransferHistoryScreen(
                career = current,
                onBack = { navController.popBackStack() },
                onOpenPlayer = { id -> navController.navigate(Routes.playerDetail(id)) }
            )
        }

        // ------------------------------------------------------- tactics
        composable(Routes.TACTICS) {
            if (career != null) {
                TacticsScreen(
                    career = career,
                    onSetFormation = { viewModel.setFormation(it) },
                    onSetMentality = { viewModel.setMentality(it) },
                    onSetStyle = { viewModel.setStyle(it) },
                    onSetDefensiveLine = { viewModel.setDefensiveLine(it) },
                    onSetTempo = { viewModel.setTempo(it) },
                    onSetTactics = { viewModel.setTactics(it) },
                    onSetTrainingFocus = { viewModel.setTrainingFocus(it) },
                    onApplyPreset = { viewModel.applyPreset(it) },
                    onSetWidth = { viewModel.setWidth(it) },
                    onSetPressing = { viewModel.setPressing(it) },
                    onSetPassing = { viewModel.setPassingStyle(it) },
                    onSetBuildUp = { viewModel.setBuildUp(it) },
                    onSetCounterAttack = { viewModel.setCounterAttack(it) },
                    onSetPossessionFocus = { viewModel.setPossessionFocus(it) },
                    onSetCrossing = { viewModel.setCrossing(it) },
                    onSetAggression = { viewModel.setAggression(it) },
                    onSetIndividualInstruction = { id, inst -> viewModel.setIndividualInstruction(id, inst) },
                    onSetSetPieces = { viewModel.setSetPieces(it) },
                    onAutoPick = { viewModel.autoPickSelection() },
                    onAssignSlot = { slot, player -> viewModel.assignPlayerToSlot(slot, player) },
                    onRemoveFromSlot = { viewModel.removePlayerFromSlot(it) },
                    onSwapSlots = { a, b -> viewModel.swapSlots(a, b) },
                    onSetCaptain = { viewModel.setCaptain(it) },
                    onToggleSubstitute = { viewModel.toggleSubstitute(it) }
                )
            }
        }

        // ----------------------------------------------------- transfers
        composable(Routes.TRANSFERS) {
            if (career != null) {
                val sellTarget by viewModel.sellTarget.collectAsStateWithLifecycle()
                TransfersScreen(
                    career = career,
                    onSearch = { query, position -> viewModel.searchTransferMarket(query, position) },
                    onAskingPrice = { viewModel.askingPriceFor(it) },
                    onExpectedWage = { viewModel.expectedWageFor(it) },
                    onRequiredPackage = { viewModel.requiredPackageFor(it) },
                    onMakeOfferPackage = { playerId, offerPackage, terms ->
                        viewModel.makeOfferPackage(playerId, offerPackage, terms)
                    },
                    onAcceptCounter = { viewModel.acceptCounter(it) },
                    onSubmitPlayerTerms = { offerId, terms -> viewModel.submitPlayerTerms(offerId, terms) },
                    onCancelOffer = { viewModel.cancelOffer(it) },
                    onListPlayer = { playerId, asking -> viewModel.listPlayerForSale(playerId, asking) },
                    onSetAskingPrice = { playerId, asking -> viewModel.setAskingPrice(playerId, asking) },
                    onInterestedCount = { player, asking -> viewModel.interestedClubCount(player, asking) },
                    onCounterSaleBid = { clubId, amount -> viewModel.counterSaleBid(clubId, amount) },
                    onAcceptSaleBid = { clubId -> viewModel.acceptSaleBid(clubId) },
                    onRejectSaleBid = { clubId -> viewModel.rejectSaleBid(clubId) },
                    onCancelSale = { viewModel.cancelSale() },
                    onRelease = { viewModel.releasePlayer(it) },
                    onPlayersForClub = { clubId -> viewModel.playersForClub(clubId) },
                    onOpenNegotiation = { id ->
                        viewModel.markNegotiationRead(id)
                        navController.navigate(Routes.negotiationDetail(id))
                    },
                    onMarkAllNegotiationsRead = { viewModel.markAllNegotiationsRead() },
                    onOpenHistory = { navController.navigate(Routes.TRANSFER_HISTORY) },
                    initialTab = if (sellTarget != null) TransferTab.SELL else TransferTab.BUY,
                    preselectPlayerId = sellTarget,
                    onConsumePreselect = { viewModel.consumeSellTarget() }
                )
            }
        }

        // ---------------------------------------------------------- more
        composable(Routes.MORE) {
            if (career != null) {
                MoreScreen(
                    career = career,
                    onNavigate = { navController.navigate(it) },
                    onSave = { viewModel.saveCareer() },
                    onMainMenu = {
                        viewModel.returnToMainMenu()
                        navController.navigate(Routes.MAIN_MENU) {
                            popUpTo(Routes.MAIN_MENU) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Routes.LEAGUE) {
            if (career != null) {
                CompetitionHubScreen(
                    career = career,
                    onOpenLeague = { navController.navigate(Routes.LEAGUE_TABLE) },
                    onOpenLeagueById = { id -> navController.navigate(Routes.leagueTable(id)) },
                    onOpenChampionsLeague = { navController.navigate(Routes.CHAMPIONS_LEAGUE) },
                    onOpenEurope = { navController.navigate(Routes.EUROPE) },
                    onOpenCompetition = { competition ->
                        navController.navigate(Routes.europe(competition.name))
                    },
                    onOpenFixtures = { navController.navigate(Routes.FIXTURES) }
                )
            }
        }

        composable(Routes.LEAGUE_TABLE) {
            if (career != null) {
                LeagueScreen(
                    career = career,
                    onOpenFixtures = { navController.navigate(Routes.FIXTURES) },
                    onOpenClub = { id -> navController.navigate(Routes.clubProfile(id)) }
                )
            }
        }

        composable(
            route = Routes.LEAGUE_TABLE_COMPETITION,
            arguments = listOf(navArgument("leagueId") { type = NavType.StringType })
        ) { entry ->
            val leagueId = entry.arguments?.getString("leagueId")
            if (career != null) {
                LeagueScreen(
                    career = career,
                    onOpenFixtures = { navController.navigate(Routes.FIXTURES) },
                    onOpenClub = { id -> navController.navigate(Routes.clubProfile(id)) },
                    initialLeagueId = leagueId
                )
            }
        }

        composable(Routes.FIXTURES) {
            if (career != null) FixturesScreen(career = career)
        }

        composable(Routes.CHAMPIONS_LEAGUE) {
            if (career != null) {
                com.footymanager.simulator.ui.screens.ChampionsLeagueScreen(
                    career = career,
                    onOpenClub = { id -> navController.navigate(Routes.clubProfile(id)) }
                )
            }
        }

        composable(Routes.EUROPE) {
            if (career != null) {
                com.footymanager.simulator.ui.screens.EuropeanCompetitionScreen(
                    career = career,
                    competition = com.footymanager.simulator.domain.model.CompetitionType.CHAMPIONS_LEAGUE,
                    onOpenClub = { id -> navController.navigate(Routes.clubProfile(id)) }
                )
            }
        }

        composable(
            route = Routes.EUROPE_COMPETITION,
            arguments = listOf(navArgument("competition") { type = NavType.StringType })
        ) { entry ->
            val competitionName = entry.arguments?.getString("competition")
            val competition = com.footymanager.simulator.domain.model.CompetitionType.entries
                .firstOrNull { it.name == competitionName }
                ?: com.footymanager.simulator.domain.model.CompetitionType.CHAMPIONS_LEAGUE
            if (career != null) {
                com.footymanager.simulator.ui.screens.EuropeanCompetitionScreen(
                    career = career,
                    competition = competition,
                    onOpenClub = { id -> navController.navigate(Routes.clubProfile(id)) }
                )
            }
        }

        composable(Routes.STADIUM) {
            if (career != null) {
                com.footymanager.simulator.ui.screens.StadiumScreen(
                    career = career,
                    onSetTicketPrice = { viewModel.setTicketPrice(it) },
                    onExpand = { viewModel.upgradeStadium() }
                )
            }
        }

        composable(Routes.SPONSORS) {
            if (career != null) {
                com.footymanager.simulator.ui.screens.SponsorsScreen(
                    career = career,
                    onSign = { viewModel.signSponsorship(it) }
                )
            }
        }

        composable(Routes.REWARDS) {
            val rewards = viewModel.adRewards.collectAsStateWithLifecycle().value
            if (career != null && rewards != null) {
                RewardsScreen(
                    state = rewards,
                    onBack = { navController.popBackStack() },
                    onClaim = { viewModel.claimAdReward(it) }
                )
            }
        }

        composable(Routes.FINANCES) {
            if (career != null) FinancesScreen(career = career)
        }

        composable(Routes.BOARD) {
            if (career != null) BoardScreen(career = career)
        }

        composable(Routes.NEWS) {
            if (career != null) NewsScreen(career = career)
        }

        composable(Routes.STATISTICS) {
            if (career != null) StatisticsScreen(career = career)
        }

        composable(Routes.TRAINING) {
            if (career != null) {
                TrainingScreen(career = career, onSetFocus = { viewModel.setTrainingFocus(it) })
            }
        }

        composable(Routes.SEASON_SUMMARY) {
            if (career != null) {
                SeasonSummaryScreen(
                    career = career,
                    onStartNextSeason = {
                        viewModel.startNextSeason()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                hasCareer = career != null,
                onBack = { navController.popBackStack() },
                onSetSound = { viewModel.setSound(it) },
                onSetVibration = { viewModel.setVibration(it) },
                onSetDarkTheme = { viewModel.setDarkTheme(it) },
                onSetDifficulty = { viewModel.setDifficulty(it) },
                onSetAnimationSpeed = { viewModel.setAnimationSpeed(it) },
                onSetMusic = { viewModel.setMusic(it) },
                onSetMusicTrack = { viewModel.setMusicTrack(it) },
                onSetMusicPlayMode = { viewModel.setMusicPlayMode(it) },
                onPreviewMusic = { viewModel.previewMusic() },
                onStopMusicPreview = { viewModel.stopMusicPreview() },
                onSetSoundVolume = { viewModel.setSoundVolume(it) },
                onSetMusicVolume = { viewModel.setMusicVolume(it) },
                onResetCareer = {
                    viewModel.resetCareer()
                    navController.navigate(Routes.MAIN_MENU) {
                        popUpTo(Routes.MAIN_MENU) { inclusive = true }
                    }
                },
                onAbout = onShowAbout
            )
        }

        // ------------------------------------------------------ match day
        composable(Routes.MATCH_DAY) {
            if (career != null && matchDay != null) {
                MatchDayScreen(
                    career = career,
                    matchDay = matchDay,
                    onBack = {
                        // Leaving a live match must never abandon it: the current
                        // engine state is captured into the save and the next time
                        // the fixture is opened it resumes at the same minute.
                        // A finished match advances the calendar as before.
                        if (matchDay.isPlayed) {
                            viewModel.advanceAfterMatch()
                        } else if (matchDay.started && !viewModel.hasLiveMatch) {
                            // Paused, half-time or awaiting extra time: keep the
                            // snapshot already written and just close the screen.
                        } else if (matchDay.started) {
                            viewModel.leaveMatch()
                        } else {
                            viewModel.cancelMatch()
                        }
                        navController.popBackStack()
                    },
                    onStart = { mode -> viewModel.startMatch(mode) },
                    onContinueSecondHalf = { viewModel.continueSecondHalf() },
                    onContinueExtraTime = { viewModel.continueExtraTime() },
                    onPause = { viewModel.pauseMatch() },
                    onResume = { viewModel.resumeMatch() },
                    onQuickSimFromHere = { viewModel.quickSimFromHere() },
                    onMakeLiveSub = { off, on -> viewModel.makeLiveSubstitution(off, on) },
                    onPlanSub = { off, on -> viewModel.planSubstitution(off, on) },
                    onCancelSub = { viewModel.cancelSubstitution(it) },
                    onApplyLiveTactics = { viewModel.applyLiveTactics(it) },
                    onContinueAfterMatch = {
                        viewModel.advanceAfterMatch()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                )
            } else {
                // The match-day state is gone (e.g. process death): send the player home.
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            }
        }

        // ------------------------------------------------- simulate to date
        composable(Routes.SIMULATE_TO_DATE) {
            if (career == null) {
                EmptyCareerState(onReturn = {
                    navController.navigate(Routes.MAIN_MENU) {
                        popUpTo(Routes.MAIN_MENU) { inclusive = true }
                    }
                })
            } else {
                SimulateToDateScreen(
                    career = career,
                    onBack = { navController.popBackStack() },
                    onConfirm = { target ->
                        viewModel.startSimulateToDate(target)
                        navController.navigate(Routes.SIMULATE_PROGRESS) {
                            popUpTo(Routes.SIMULATE_TO_DATE) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Routes.SIMULATE_PROGRESS) {
            val state = simulateToDate
            if (state == null) {
                // The simulation has not started (or was cleared): go home.
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            } else if (state.finished) {
                val summary = state.summary
                if (summary != null) {
                    SimulateSummaryScreen(
                        summary = summary,
                        onContinue = {
                            viewModel.clearSimulateToDate()
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        viewModel.clearSimulateToDate()
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.HOME) { inclusive = true }
                        }
                    }
                }
            } else {
                SimulateProgressScreen(
                    state = state,
                    onContinue = { viewModel.finishSimulateReveal() }
                )
            }
        }
    }
}

@Composable
private fun EmptyCareerState(onReturn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No active career",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Start a new career or continue a saved one from the main menu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        com.footymanager.simulator.ui.components.FmPrimaryButton(
            text = "Return to Main Menu",
            onClick = onReturn
        )
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}
