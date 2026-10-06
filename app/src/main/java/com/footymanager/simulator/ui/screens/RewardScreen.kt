package com.footymanager.simulator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.AdRewardState
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmPrimaryButton
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import kotlinx.coroutines.delay

/**
 * Optional rewarded-ad screen.
 *
 * No advertising SDK is linked in the first release, so the "ad" is a short
 * simulated countdown handled by [RewardAdProvider]. Dropping in AdMob later
 * means implementing that one interface with the real SDK; nothing else in the
 * app needs to change, and the reward allowance is already persisted per day.
 */
@Composable
fun RewardsScreen(
    state: AdRewardState,
    clubReputation: Int,
    onBack: () -> Unit,
    onClaim: () -> Long
) {
    var playing by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf(0) }
    var claimed by remember { mutableStateOf(0L) }

    // Drives the mock ad playback. The real provider would expose progress via
    // callbacks; the countdown keeps the same shape so the UI does not change.
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        remaining = RewardAdProvider.MOCK_DURATION_SECONDS
        while (remaining > 0) {
            delay(1000)
            remaining -= 1
        }
        claimed = onClaim()
        playing = false
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Bonus Rewards",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item {
            FmCard(accent = MaterialTheme.colorScheme.primary) {
                SectionHeader("Daily allowance")
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("Watched today", "${state.adsWatchedToday}/${state.maxPerDay}", Modifier.weight(1f))
                    StatCell("Left today", state.remainingToday.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Up to ${Fmt.money(AdRewardState.maxDailyTotal(clubReputation))} a day, free. " +
                        "Rewards reset each calendar day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FmCard {
                SectionHeader("How it works")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Watch a short advert to top up the club balance. " +
                        "This is always optional and never required to play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                if (playing) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = {
                                1f - remaining.toFloat() / RewardAdProvider.MOCK_DURATION_SECONDS
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "Playing advert… ${remaining}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val exhausted = state.remainingToday <= 0
                    FmPrimaryButton(
                        text = when {
                            exhausted -> "Come back tomorrow"
                            else -> "Watch advert for ${Fmt.money(state.nextReward(clubReputation))}"
                        },
                        onClick = { playing = true },
                        enabled = !exhausted,
                        icon = Icons.Filled.PlayArrow
                    )
                }

                if (claimed > 0L) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${Fmt.money(claimed)} added to the club balance.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Lifetime")
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("Ads watched", state.totalAdsWatched.toString(), Modifier.weight(1f))
                    StatCell("Total earned", Fmt.money(state.totalRewardsEarned), Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Stand-in for a real rewarded-ad SDK. A future AdMob integration implements the
 * same surface, so the screen above stays unchanged.
 */
object RewardAdProvider {
    /** Length of the simulated advert in seconds. */
    const val MOCK_DURATION_SECONDS = 5
}
