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
import com.footymanager.simulator.ui.screens.SquadScreen
import com.footymanager.simulator.ui.screens.StatisticsScreen
import com.footymanager.simulator.ui.screens.TacticsScreen
import com.footymanager.simulator.ui.screens.TrainingScreen
import com.footymanager.simulator.ui.screens.TransfersScreen
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
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    FootballManagerTheme(darkTheme = settings.darkTheme) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        var showAbout by remember { mutableStateOf(false) }

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

@Composable
private fun AppNavHost(
    navController: NavHostController,
    viewModel: GameViewModel,
    career: com.footymanager.simulator.domain.model.Career?,
    matchDay: com.footymanager.simulator.viewmodel.MatchDayState?,
    hasSave: Boolean,
    settings: com.footymanager.simulator.domain.data.GameSettings,
    isBusy: Boolean,
    onShowAbout: () -> Unit
) {
    val clubs = remember { ClubDatabase.buildAll() }

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN_MENU,
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(140)) }
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
                    onOpenSquad = { navController.navigate(Routes.SQUAD) },
                    onOpenLeague = { navController.navigate(Routes.LEAGUE) },
                    onOpenNews = { navController.navigate(Routes.NEWS) },
                    onOpenBoard = { navController.navigate(Routes.BOARD) },
                    onOpenSponsors = { navController.navigate(Routes.SPONSORS) },
                    onStartNextSeason = { navController.navigate(Routes.SEASON_SUMMARY) }
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
                    onTransferList = {
                        viewModel.releasePlayer(it)
                    }
                )
            }
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
                    onAutoPick = { viewModel.autoPickSelection() },
                    onAssignSlot = { slot, player -> viewModel.assignPlayerToSlot(slot, player) },
                    onRemoveFromSlot = { viewModel.removePlayerFromSlot(it) },
                    onSetCaptain = { viewModel.setCaptain(it) },
                    onToggleSubstitute = { viewModel.toggleSubstitute(it) }
                )
            }
        }

        // ----------------------------------------------------- transfers
        composable(Routes.TRANSFERS) {
            if (career != null) {
                TransfersScreen(
                    career = career,
                    onSearch = { query, position -> viewModel.searchTransferMarket(query, position) },
                    onAskingPrice = { viewModel.askingPriceFor(it) },
                    onExpectedWage = { viewModel.expectedWageFor(it) },
                    onMakeOffer = { playerId, fee, wage, years ->
                        viewModel.makeOffer(playerId, fee, wage, years)
                    },
                    onResolveOffer = { viewModel.resolveOffer(it) },
                    onWithdrawOffer = { viewModel.withdrawOffer(it) },
                    onInterestedBuyers = { viewModel.interestedBuyers(it) },
                    onSell = { playerId, fee, buyerId -> viewModel.sellPlayer(playerId, fee, buyerId) },
                    onRelease = { viewModel.releasePlayer(it) }
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
                    onOpenChampionsLeague = { navController.navigate(Routes.CHAMPIONS_LEAGUE) },
                    onOpenFixtures = { navController.navigate(Routes.FIXTURES) }
                )
            }
        }

        composable(Routes.LEAGUE_TABLE) {
            if (career != null) {
                LeagueScreen(career = career, onOpenFixtures = { navController.navigate(Routes.FIXTURES) })
            }
        }

        composable(Routes.FIXTURES) {
            if (career != null) FixturesScreen(career = career)
        }

        composable(Routes.CHAMPIONS_LEAGUE) {
            if (career != null) {
                com.footymanager.simulator.ui.screens.ChampionsLeagueScreen(career = career)
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
                        // Only advance the calendar if the match was actually played.
                        if (matchDay.isPlayed) viewModel.advanceAfterMatch()
                        else viewModel.cancelMatch()
                        navController.popBackStack()
                    },
                    onStart = { mode -> viewModel.startMatch(mode) },
                    onContinueSecondHalf = { viewModel.continueSecondHalf() },
                    onContinueExtraTime = { viewModel.continueExtraTime() },
                    onPause = { viewModel.pauseMatch() },
                    onResume = { viewModel.resumeMatch() },
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
