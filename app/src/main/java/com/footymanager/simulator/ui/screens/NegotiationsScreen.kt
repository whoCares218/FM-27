package com.footymanager.simulator.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Career
import com.footymanager.simulator.domain.model.NegotiationOutcome
import com.footymanager.simulator.domain.model.NegotiationRecord
import com.footymanager.simulator.domain.model.NegotiationSide
import com.footymanager.simulator.domain.model.NegotiationStatus
import com.footymanager.simulator.ui.components.EmptyState
import com.footymanager.simulator.ui.components.FmCard
import com.footymanager.simulator.ui.components.Fmt
import com.footymanager.simulator.ui.components.PositionChip
import com.footymanager.simulator.ui.components.SectionHeader
import com.footymanager.simulator.ui.theme.StatColors
import androidx.compose.material.icons.outlined.SwapHoriz

/** The four buckets the desk is organised into. */
enum class NegotiationFilter(val label: String) {
    ALL("All"),
    ONGOING("Ongoing"),
    COMPLETED("Completed"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn")
}

/**
 * The manager's transfer desk: a live inbox of every negotiation on both the buy
 * and sell sides, grouped by status with unread indicators and a header summary.
 */
@Composable
fun NegotiationsSection(
    career: Career,
    onOpen: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenHistory: () -> Unit = {}
) {
    val all = remember(career.negotiations) { career.negotiations.sortedByDescending { it.id } }
    val unread = all.count { it.unread }
    val active = all.count { it.status.isLive }
    val completed = all.count { it.outcome == NegotiationOutcome.COMPLETED }
    val rejected = all.count { it.outcome == NegotiationOutcome.REJECTED }
    val withdrawn = all.count { it.outcome == NegotiationOutcome.WITHDRAWN }

    var filter by remember { mutableStateOf(NegotiationFilter.ALL) }

    val visible = when (filter) {
        NegotiationFilter.ALL -> all
        NegotiationFilter.ONGOING -> all.filter { it.outcome == NegotiationOutcome.ONGOING }
        NegotiationFilter.COMPLETED -> all.filter { it.outcome == NegotiationOutcome.COMPLETED }
        NegotiationFilter.REJECTED -> all.filter { it.outcome == NegotiationOutcome.REJECTED }
        NegotiationFilter.WITHDRAWN -> all.filter { it.outcome == NegotiationOutcome.WITHDRAWN }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            FmCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "TRANSFER DESK",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$active Active · $completed Completed · $rejected Rejected" +
                                if (withdrawn > 0) " · $withdrawn Withdrawn" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (unread > 0) {
                        TextButton(onClick = onMarkAllRead) {
                            Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mark read", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("View transfer history", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(NegotiationFilter.entries.toList(), key = { it.name }) { option ->
                    val count = when (option) {
                        NegotiationFilter.ALL -> all.size
                        NegotiationFilter.ONGOING -> active
                        NegotiationFilter.COMPLETED -> completed
                        NegotiationFilter.REJECTED -> rejected
                        NegotiationFilter.WITHDRAWN -> withdrawn
                    }
                    DeskChip(
                        label = "${option.label} ($count)",
                        selected = filter == option,
                        onClick = { filter = option }
                    )
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.SwapHoriz,
                    title = "No negotiations yet",
                    body = "Bid for a player on the Buy tab, or list a player for sale on the Sell tab, " +
                        "and the discussion will appear here."
                )
            }
        } else {
            items(visible, key = { it.id }) { record ->
                NegotiationRow(record = record, onClick = { onOpen(record.id) })
            }
        }
    }
}

@Composable
private fun DeskChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

/** A single negotiation row, with a status accent and progress bar. */
@Composable
fun NegotiationRow(record: NegotiationRecord, onClick: () -> Unit) {
    val accent = negotiationStatusColor(record.status)
    FmCard(onClick = onClick, accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(record.unread, accent)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.playerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    PositionChip(record.playerPosition)
                    if (record.unread) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .size(8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = record.direction(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OfferLine(
                        label = if (record.side == NegotiationSide.BUY) "Offer" else "Bid",
                        value = Fmt.money(record.latestOffer)
                    )
                    if (record.latestCounter > 0 && record.latestCounter != record.latestOffer) {
                        OfferLine(label = "Counter", value = Fmt.money(record.latestCounter))
                    }
                    OfferLine(label = "Value", value = Fmt.money(record.marketValue))
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar(record.progress, accent)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                StatusPill(record.status)
                Spacer(Modifier.height(6.dp))
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun OfferLine(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ProgressBar(progress: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "negotiationProgress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
    }
}

@Composable
fun StatusDot(unread: Boolean, color: Color) {
    val animated by animateColorAsState(
        targetValue = if (unread) color else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "statusDot"
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(animated)
    )
}

@Composable
fun StatusPill(status: NegotiationStatus) {
    val color = negotiationStatusColor(status)
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = status.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/** A calm, distinct colour per status without over-saturating the screen. */
@Composable
fun negotiationStatusColor(status: NegotiationStatus): Color = when (status) {
    NegotiationStatus.ONGOING -> StatColors.average
    NegotiationStatus.NEGOTIATING -> StatColors.poor
    NegotiationStatus.ACCEPTED -> StatColors.good
    NegotiationStatus.COMPLETED -> StatColors.elite
    NegotiationStatus.REJECTED -> StatColors.bad
    NegotiationStatus.WITHDRAWN -> MaterialTheme.colorScheme.onSurfaceVariant
}
