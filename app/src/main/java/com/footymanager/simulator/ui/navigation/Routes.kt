package com.footymanager.simulator.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector

/** Every destination in the app, including the main menu and match-day flow. */
object Routes {
    const val MAIN_MENU = "main_menu"
    const val NEW_CAREER = "new_career"
    const val SETTINGS = "settings"
    const val HOW_TO_PLAY = "how_to_play"

    const val HOME = "home"
    const val SQUAD = "squad"
    const val TACTICS = "tactics"
    const val TRANSFERS = "transfers"
    const val LEAGUE = "league"
    const val LEAGUE_TABLE = "league_table"
    const val MORE = "more"

    const val PLAYER_DETAIL = "player/{playerId}"
    const val CLUB_PROFILE = "club/{clubId}"
    const val NEGOTIATION_DETAIL = "negotiation/{negotiationId}"
    const val TRANSFER_HISTORY = "transfer_history"
    const val MATCH_DAY = "match_day"
    const val SEASON_SUMMARY = "season_summary"
    const val FINANCES = "finances"
    const val BOARD = "board"
    const val NEWS = "news"
    const val STATISTICS = "statistics"
    const val TRAINING = "training"
    const val FIXTURES = "fixtures"
    const val CHAMPIONS_LEAGUE = "champions_league"
    const val STADIUM = "stadium"
    const val SPONSORS = "sponsors"
    const val REWARDS = "rewards"

    fun playerDetail(playerId: Long) = "player/$playerId"

    fun clubProfile(clubId: Long) = "club/$clubId"

    fun negotiationDetail(negotiationId: Long) = "negotiation/$negotiationId"
}

/** The six tabs of the bottom navigation bar. */
enum class BottomTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    SQUAD(Routes.SQUAD, "Squad", Icons.Filled.Groups, Icons.Outlined.Groups),
    TACTICS(Routes.TACTICS, "Tactics", Icons.Filled.Tune, Icons.Outlined.Tune),
    TRANSFERS(Routes.TRANSFERS, "Transfers", Icons.Filled.SwapHoriz, Icons.Outlined.SwapHoriz),
    LEAGUE(Routes.LEAGUE, "League", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents),
    MORE(Routes.MORE, "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz);

    companion object {
        fun fromRoute(route: String?): BottomTab? = entries.firstOrNull { it.route == route }
    }
}

/** Secondary destinations reachable from the More tab. */
data class MoreDestination(
    val route: String,
    val label: String,
    val description: String,
    val icon: ImageVector
)

val moreDestinations: List<MoreDestination> = listOf(
    MoreDestination(Routes.FIXTURES, "Fixtures", "Your full season schedule", Icons.Outlined.Groups),
    MoreDestination(Routes.TRAINING, "Training", "Set the weekly training focus", Icons.Outlined.Tune),
    MoreDestination(Routes.STADIUM, "Stadium", "Capacity, tickets and expansion", Icons.Outlined.Home),
    MoreDestination(Routes.SPONSORS, "Sponsorship", "Sign a season deal", Icons.Outlined.SwapHoriz),
    MoreDestination(Routes.REWARDS, "Bonus Rewards", "Watch a short ad for club funds", Icons.Outlined.Star),
    MoreDestination(Routes.FINANCES, "Finances", "Balance, budgets and transfers", Icons.Outlined.SwapHoriz),
    MoreDestination(Routes.BOARD, "Board", "Confidence and season objectives", Icons.Outlined.Home),
    MoreDestination(Routes.STATISTICS, "Statistics", "Club and player numbers", Icons.Outlined.EmojiEvents),
    MoreDestination(Routes.NEWS, "News", "Latest stories from the club", Icons.Outlined.Home),
    MoreDestination(Routes.SETTINGS, "Settings", "Sound, theme and difficulty", Icons.Outlined.MoreHoriz)
)
