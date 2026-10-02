package com.footymanager.simulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.FmPrimaryButton
import com.footymanager.simulator.ui.components.FmSecondaryButton
import com.footymanager.simulator.ui.components.Fmt

/**
 * The professional home screen: title, current season, and the five entry points.
 */
@Composable
fun MainMenuScreen(
    hasSave: Boolean,
    savedCareer: Career?,
    onNewCareer: () -> Unit,
    onContinue: () -> Unit,
    onLoadGame: () -> Unit,
    onSettings: () -> Unit,
    onHowToPlay: () -> Unit
) {
    var showLoadDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 48.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---- Brand block ----
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.SportsSoccer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "FOOTBALL MANAGER",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "26/27",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "SIMULATOR",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SEASON ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Career.CURRENT_SEASON,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(30.dp))

            // ---- Actions ----
            FmPrimaryButton(
                text = "New Career",
                onClick = onNewCareer,
                icon = Icons.Outlined.PlayCircle
            )

            Spacer(Modifier.height(10.dp))

            FmSecondaryButton(
                text = if (hasSave) "Continue Career" else "Continue Career (no save)",
                onClick = onContinue,
                enabled = hasSave,
                icon = Icons.Filled.PlayArrow
            )

            Spacer(Modifier.height(10.dp))

            FmSecondaryButton(
                text = "Load Game",
                onClick = { showLoadDialog = true },
                enabled = hasSave,
                icon = Icons.Outlined.PlayCircle
            )

            Spacer(Modifier.height(10.dp))

            FmSecondaryButton(
                text = "Settings",
                onClick = onSettings,
                icon = Icons.Outlined.Settings
            )

            Spacer(Modifier.height(10.dp))

            FmSecondaryButton(
                text = "How to Play",
                onClick = onHowToPlay,
                icon = Icons.Outlined.Info
            )

            // ---- Saved career summary ----
            if (savedCareer != null) {
                Spacer(Modifier.height(24.dp))
                FmCard(accent = MaterialTheme.colorScheme.primary) {
                    Text(
                        text = "SAVED CAREER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${savedCareer.managerName} — ${savedCareer.userClub.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${savedCareer.season} • Matchday ${savedCareer.matchdayIndex + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Budget ${Fmt.money(savedCareer.userClub.transferBudget)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Works fully offline. No account required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (showLoadDialog) {
            AlertDialog(
                onDismissRequest = { showLoadDialog = false },
                title = { Text("Load saved career?") },
                text = {
                    Text(
                        "This discards any unsaved progress in the current session and " +
                            "reloads the career stored on this device."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showLoadDialog = false
                        onLoadGame()
                    }) { Text("Load") }
                },
                dismissButton = {
                    TextButton(onClick = { showLoadDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}
