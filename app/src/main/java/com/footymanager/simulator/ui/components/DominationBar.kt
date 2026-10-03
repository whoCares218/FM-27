package com.footymanager.simulator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Club

/**
 * The live domination bar.
 *
 * The engine only updates the underlying domination figure once per simulated
 * minute. This composable animates toward that figure every frame via Compose's
 * frame-driven [animateFloatAsState], so the bar slides smoothly and never
 * stutters, without the engine doing any per-frame work.
 *
 * Each side's share is drawn as a filled segment in that club's colour, so a
 * white club and a black club are both visible: the segments always carry a
 * contrasting outline and the labels sit on their own segment.
 */
@Composable
fun DominationBar(
    homeClub: Club,
    awayClub: Club,
    homeShare: Int,
    modifier: Modifier = Modifier,
    labelHome: String = "HOME",
    labelAway: String = "AWAY",
    animated: Boolean = true
) {
    val target = homeShare.coerceIn(0, 100) / 100f
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (animated) 900 else 0),
        label = "domination"
    )

    val homePalette = badgePalette(homeClub)
    val awayPalette = badgePalette(awayClub)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = homeClub.shortName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(fraction * 100).toInt()}%  ·  ${(100 - fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = awayClub.shortName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Home segment.
            Box(
                modifier = Modifier
                    .weight(fraction.coerceIn(0.02f, 0.98f))
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(homePalette.plate, homePalette.plate.copy(alpha = 0.82f))
                        )
                    )
            )
            // Away segment.
            Box(
                modifier = Modifier
                    .weight((1f - fraction).coerceIn(0.02f, 0.98f))
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(awayPalette.plate.copy(alpha = 0.82f), awayPalette.plate)
                        )
                    )
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = labelHome,
                style = MaterialTheme.typography.labelSmall,
                color = homePalette.plate.copy(alpha = 0.95f),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "DOMINATION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = labelAway,
                style = MaterialTheme.typography.labelSmall,
                color = awayPalette.plate.copy(alpha = 0.95f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** A slim inline version for compact cards. */
@Composable
fun DominationBarCompact(
    homeColor: Color,
    awayColor: Color,
    homeShare: Int,
    modifier: Modifier = Modifier
) {
    val fraction by animateFloatAsState(
        targetValue = homeShare.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 700),
        label = "dominationCompact"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .weight(fraction.coerceIn(0.02f, 0.98f))
                .fillMaxWidth()
                .height(8.dp)
                .background(homeColor)
        )
        Box(
            modifier = Modifier
                .weight((1f - fraction).coerceIn(0.02f, 0.98f))
                .fillMaxWidth()
                .height(8.dp)
                .background(awayColor)
        )
    }
}
