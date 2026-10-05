package com.footymanager.simulator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.TransferDirection
import com.footymanager.simulator.domain.model.TransferHistoryEntry
import com.footymanager.simulator.ui.components.EmptyState
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.PositionChip
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.theme.StatColors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.SwapHoriz

enum class HistorySort(val label: String) {
    NEWEST("Newest"),
    HIGHEST_FEE("Highest fee"),
    PLAYER("Player"),
    CLUB("Club")
}

/** The manager's permanent record of completed transfers in and out of the club. */
@Composable
fun TransferHistoryScreen(
    career: Career,
    onBack: () -> Unit,
    onOpenPlayer: (Long) -> Unit
) {
    var sort by remember { mutableStateOf(HistorySort.NEWEST) }
    val history = remember(career.transferHistory, sort) {
        when (sort) {
            HistorySort.NEWEST -> career.transferHistory.sortedByDescending { it.id }
            HistorySort.HIGHEST_FEE -> career.transferHistory.sortedByDescending { it.totalValue }
            HistorySort.PLAYER -> career.transferHistory.sortedBy { it.playerName }
            HistorySort.CLUB -> career.transferHistory.sortedBy { it.fromClubName + it.toClubName }
        }
    }
    val spent = career.transferHistory.filter { it.direction == TransferDirection.IN }.sumOf { it.fee }
    val received = career.transferHistory.filter { it.direction == TransferDirection.OUT }.sumOf { it.fee }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.width(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Transfer history",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item {
            FmCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Deals", "${career.transferHistory.size}")
                    StatCell("Spent", Fmt.money(spent), valueColor = StatColors.poor)
                    StatCell("Received", Fmt.money(received), valueColor = StatColors.elite)
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(HistorySort.entries.toList(), key = { it.name }) { option ->
                    HistorySortChip(option.label, sort == option) { sort = option }
                }
            }
        }

        if (history.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "No completed transfers",
                    body = "Once you sign or sell a player, the deal will be recorded here with the full package."
                )
            }
        } else {
            items(history, key = { it.id }) { entry ->
                TransferHistoryCard(entry = entry, onClick = { onOpenPlayer(entry.playerId) })
            }
        }
    }
}

@Composable
private fun HistorySortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransferHistoryCard(entry: TransferHistoryEntry, onClick: () -> Unit) {
    val accent = if (entry.direction == TransferDirection.IN) StatColors.elite else StatColors.poor
    FmCard(onClick = onClick, accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.playerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(6.dp))
                    PositionChip(entry.position)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${entry.fromClubName}  →  ${entry.toClubName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Fmt.money(entry.totalValue),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = if (entry.direction == TransferDirection.IN) "SIGNED" else "SOLD",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatCell("Fee", Fmt.money(entry.fee))
            StatCell("Wage", Fmt.wage(entry.wagePerWeek))
            StatCell("Length", "${entry.contractYears}y")
            StatCell("Date", "${entry.date.short()} ${entry.season.take(4)}")
        }
        if (entry.exchangedPlayerName.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Part exchange: ${entry.exchangedPlayerName} (${Fmt.money(entry.exchangedPlayerValue)})",
                style = MaterialTheme.typography.labelSmall,
                color = StatColors.average
            )
        }
    }
}
