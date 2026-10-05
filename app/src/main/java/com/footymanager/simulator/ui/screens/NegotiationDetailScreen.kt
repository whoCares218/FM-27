package com.footymanager.simulator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.NegotiationEvent
import com.footymanager.simulator.domain.model.NegotiationEventKind
import com.footymanager.simulator.domain.model.NegotiationRecord
import com.footymanager.simulator.domain.model.NegotiationSide
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.PositionChip
import com.footymanager.simulator.ui.components.RatingBadge
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.components.StatCell
import com.footymanager.simulator.ui.theme.StatColors

/**
 * The full detail of one negotiation: the money, the people, the positions of
 * each side and a vertical timeline of everything that has happened.
 */
@Composable
fun NegotiationDetailScreen(
    career: Career,
    record: NegotiationRecord,
    onBack: () -> Unit
) {
    val accent = negotiationStatusColor(record.status)
    val player = career.player(record.playerId)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
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
                    text = "Negotiation",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item {
            FmCard(accent = accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingBadge(player?.overall ?: 0, size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.playerName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PositionChip(record.playerPosition)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${record.playerAge}y",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    StatusPill(record.status)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = record.direction(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (record.side == NegotiationSide.BUY) {
                        "${record.userClubName} are buying"
                    } else {
                        "${record.userClubName} are selling"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            FmCard {
                SectionHeader("The numbers")
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Market value", Fmt.money(record.marketValue))
                    StatCell("Asking price", Fmt.money(record.askingPrice), valueColor = StatColors.average)
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell("Original offer", Fmt.money(record.originalOffer))
                    StatCell("Latest offer", Fmt.money(record.latestOffer), valueColor = MaterialTheme.colorScheme.primary)
                    StatCell("Latest counter", Fmt.money(record.latestCounter), valueColor = StatColors.poor)
                }
            }
        }

        if (record.side == NegotiationSide.BUY && (record.wageProposal > 0 || record.contractYears > 0)) {
            item {
                FmCard {
                    SectionHeader("Contract proposal")
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatCell("Wage offered", Fmt.wage(record.wageProposal))
                        StatCell("Length", if (record.contractYears > 0) "${record.contractYears} years" else "—")
                    }
                }
            }
        }

        item {
            FmCard {
                SectionHeader("Where each side stands")
                Spacer(Modifier.height(10.dp))
                PositionLine(record.counterPartyClubName, clubPosition(record, accent))
                Spacer(Modifier.height(8.dp))
                PositionLine(record.playerName, playerPosition(record))
            }
        }

        item { SectionHeader("Negotiation timeline") }

        items(record.events.size) { index ->
            TimelineEntry(record.events[index], isFirst = index == 0)
        }

        item {
            Text(
                text = "Started ${record.createdDate.display()} · ${record.createdSeason}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun PositionLine(party: String, position: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = party,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = position,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun clubPosition(record: NegotiationRecord, accent: androidx.compose.ui.graphics.Color): String =
    when (record.status) {
        com.footymanager.simulator.domain.model.NegotiationStatus.COMPLETED -> "Agreed"
        com.footymanager.simulator.domain.model.NegotiationStatus.ACCEPTED -> "Accepted"
        com.footymanager.simulator.domain.model.NegotiationStatus.NEGOTIATING -> "Negotiating"
        com.footymanager.simulator.domain.model.NegotiationStatus.REJECTED -> "Rejected"
        com.footymanager.simulator.domain.model.NegotiationStatus.WITHDRAWN -> "Walking away"
        com.footymanager.simulator.domain.model.NegotiationStatus.ONGOING -> "Considering"
    }

private fun playerPosition(record: NegotiationRecord): String = when (record.status) {
    com.footymanager.simulator.domain.model.NegotiationStatus.COMPLETED -> "Signed"
    com.footymanager.simulator.domain.model.NegotiationStatus.NEGOTIATING -> "Wants more"
    com.footymanager.simulator.domain.model.NegotiationStatus.REJECTED -> "Not moving"
    com.footymanager.simulator.domain.model.NegotiationStatus.WITHDRAWN -> "—"
    else -> if (record.side == NegotiationSide.BUY) "Interested" else "Open to move"
}

/** One row of the vertical timeline, drawn with a connector line and a dot. */
@Composable
private fun TimelineEntry(event: NegotiationEvent, isFirst: Boolean) {
    val color = eventColor(event.kind)
    Row(modifier = Modifier.fillMaxWidth()) {
        // Rail: dot plus connector.
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(28.dp)) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(52.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.actor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (event.amount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Fmt.money(event.amount),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
            Text(
                text = event.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${event.date.short()} ${event.date.year} · MD${event.matchday + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun eventColor(kind: NegotiationEventKind): androidx.compose.ui.graphics.Color = when (kind) {
    NegotiationEventKind.OFFER -> StatColors.average
    NegotiationEventKind.COUNTER_CLUB -> StatColors.poor
    NegotiationEventKind.COUNTER_MANAGER -> StatColors.average
    NegotiationEventKind.TERMS -> StatColors.poor
    NegotiationEventKind.AGREEMENT -> StatColors.elite
    NegotiationEventKind.REJECTED -> StatColors.bad
    NegotiationEventKind.WITHDRAWN -> StatColors.bad
    NegotiationEventKind.INFO -> StatColors.good
}
