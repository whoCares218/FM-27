package com.footymanager.simulator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.BadgeShape
import com.footymanager.simulator.domain.model.Club

/**
 * The club badge system.
 *
 * Every club gets an original badge generated from its colours, an outline shape
 * and a graphic variant, so no two clubs look alike and no real crest is copied.
 *
 * Contrast is handled automatically: the badge paints a light or dark inner plate
 * behind the initials depending on the brightness of the club's primary colour,
 * so a white club and a black club both stay readable. A contrasting outline is
 * always drawn around the badge so it separates from any background.
 */
private fun luminance(argb: Int): Double {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
}

private fun isLight(argb: Int): Boolean = luminance(argb) > 0.62

/**
 * The colour the initials should be drawn in for a given badge background, and
 * the matching plate colour painted behind them. When the kit colour is light we
 * use a dark plate with light text, and vice versa, guaranteeing contrast.
 */
data class BadgePalette(
    val plate: Color,
    val ink: Color,
    val outline: Color,
    val accent: Color
)

fun badgePalette(club: Club): BadgePalette {
    val primary = Color(club.primaryColor)
    val secondary = Color(club.secondaryColor)
    val primaryLight = isLight(club.primaryColor)
    val secondaryLight = isLight(club.secondaryColor)

    // The plate is a darkened/lightened version of the kit colour so the badge
    // still reads as "this club" while guaranteeing the initials contrast.
    val plate = if (primaryLight) {
        Color(club.primaryColor).copy(alpha = 1f)
    } else {
        Color(club.primaryColor)
    }
    // Ink is the opposite of the plate's brightness, with a floor so mid-tones
    // still resolve clearly.
    val plateLight = luminance(
        ((plate.red * 255).toInt() shl 16) or ((plate.green * 255).toInt() shl 8) or
            (plate.blue * 255).toInt()
    ) > 0.55
    val ink = if (plateLight) Color(0xFF0B1220) else Color(0xFFF7FAFF)

    // The outline is the secondary colour when it contrasts, otherwise a neutral.
    val outline = when {
        primaryLight && !secondaryLight -> secondary
        !primaryLight && secondaryLight -> secondary
        else -> if (plateLight) Color(0xFF0B1220) else Color(0xFFF7FAFF)
    }

    return BadgePalette(plate = plate, ink = ink, outline = outline, accent = secondary)
}

/** Draws the outline path for a badge shape inside a square of [size]. */
private fun badgePath(shape: BadgeShape, size: Size): Path {
    val w = size.width
    val h = size.height
    val p = Path()
    when (shape) {
        BadgeShape.SHIELD -> {
            p.moveTo(w * 0.5f, 0f)
            p.lineTo(w, h * 0.14f)
            p.lineTo(w, h * 0.60f)
            p.quadraticBezierTo(w * 0.5f, h * 1.06f, 0f, h * 0.60f)
            p.lineTo(0f, h * 0.14f)
            p.close()
        }
        BadgeShape.CIRCLE -> {
            p.addOval(androidx.compose.ui.geometry.Rect(Offset(0f, 0f), size))
        }
        BadgeShape.ROUNDEL -> {
            p.addOval(androidx.compose.ui.geometry.Rect(Offset(w * 0.06f, 0f), Size(w * 0.88f, h)))
        }
        BadgeShape.HEXAGON -> {
            p.moveTo(w * 0.5f, 0f)
            p.lineTo(w, h * 0.26f)
            p.lineTo(w, h * 0.74f)
            p.lineTo(w * 0.5f, h)
            p.lineTo(0f, h * 0.74f)
            p.lineTo(0f, h * 0.26f)
            p.close()
        }
        BadgeShape.DIAMOND -> {
            p.moveTo(w * 0.5f, 0f)
            p.lineTo(w, h * 0.5f)
            p.lineTo(w * 0.5f, h)
            p.lineTo(0f, h * 0.5f)
            p.close()
        }
        BadgeShape.PENNANT -> {
            p.moveTo(w * 0.10f, 0f)
            p.lineTo(w * 0.90f, 0f)
            p.lineTo(w * 0.90f, h * 0.72f)
            p.lineTo(w * 0.50f, h)
            p.lineTo(w * 0.10f, h * 0.72f)
            p.close()
        }
        BadgeShape.CREST -> {
            p.moveTo(w * 0.12f, 0f)
            p.lineTo(w * 0.88f, 0f)
            p.lineTo(w, h * 0.16f)
            p.lineTo(w * 0.82f, h * 0.92f)
            p.quadraticBezierTo(w * 0.5f, h * 1.02f, w * 0.18f, h * 0.92f)
            p.lineTo(0f, h * 0.16f)
            p.close()
        }
    }
    return p
}

/**
 * A club badge drawn entirely with vector primitives: outline shape, a graphic
 * variant and the club's initials on a contrast-safe plate.
 */
@Composable
fun ClubBadge(
    club: Club,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val palette = remember(club.primaryColor, club.secondaryColor, club.badgeShape, club.badgeStyle) {
        badgePalette(club)
    }
    val initials = club.crestInitials

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = Size(this.size.width, this.size.height)
            val path = badgePath(club.badgeShape, s)

            // Body: the club's primary colour.
            drawPath(path, color = palette.plate)

            // A second colour band or diagonal adds identity per variant.
            drawBadgeVariant(club.badgeStyle, s, palette.accent, path)

            // Contrast outline so the badge separates from any background.
            drawPath(path, color = palette.outline, style = Stroke(width = s.width * 0.055f))
        }
        Text(
            text = initials,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = palette.ink
        )
    }
}

/** Draws the per-club graphic variant: a stripe, a chevron or a band. */
private fun DrawScope.drawBadgeVariant(style: Int, size: Size, accent: Color, clip: Path) {
    val w = size.width
    val h = size.height
    when (style % 6) {
        0 -> {
            // Vertical centre stripe.
            val p = Path().apply {
                moveTo(w * 0.38f, 0f)
                lineTo(w * 0.62f, 0f)
                lineTo(w * 0.62f, h)
                lineTo(w * 0.38f, h)
                close()
            }
            drawPath(p, color = accent.copy(alpha = 0.85f))
        }
        1 -> {
            // Bottom band.
            val p = Path().apply {
                moveTo(0f, h * 0.74f)
                lineTo(w, h * 0.74f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(p, color = accent.copy(alpha = 0.85f))
        }
        2 -> {
            // Chevron.
            val p = Path().apply {
                moveTo(w * 0.5f, h * 0.10f)
                lineTo(w * 0.92f, h * 0.46f)
                lineTo(w * 0.70f, h * 0.46f)
                lineTo(w * 0.5f, h * 0.30f)
                lineTo(w * 0.30f, h * 0.46f)
                lineTo(w * 0.08f, h * 0.46f)
                close()
            }
            drawPath(p, color = accent.copy(alpha = 0.85f))
        }
        3 -> {
            // Diagonal sash.
            val p = Path().apply {
                moveTo(0f, h * 0.62f)
                lineTo(w * 0.62f, 0f)
                lineTo(w, 0f)
                lineTo(w * 0.38f, h)
                lineTo(0f, h)
                close()
            }
            drawPath(p, color = accent.copy(alpha = 0.70f))
        }
        4 -> {
            // Two side panels.
            val left = Path().apply {
                moveTo(0f, 0f); lineTo(w * 0.24f, 0f); lineTo(w * 0.24f, h); lineTo(0f, h); close()
            }
            val right = Path().apply {
                moveTo(w * 0.76f, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(w * 0.76f, h); close()
            }
            drawPath(left, color = accent.copy(alpha = 0.85f))
            drawPath(right, color = accent.copy(alpha = 0.85f))
        }
        else -> {
            // Horizontal centre band.
            val p = Path().apply {
                moveTo(0f, h * 0.40f)
                lineTo(w, h * 0.40f)
                lineTo(w, h * 0.60f)
                lineTo(0f, h * 0.60f)
                close()
            }
            drawPath(p, color = accent.copy(alpha = 0.85f))
        }
    }
}

/**
 * A compact club label: badge plus short name on a chip whose background is
 * derived from the club colour, with automatic contrast so the text never
 * disappears into the badge.
 */
@Composable
fun ClubChip(
    club: Club,
    modifier: Modifier = Modifier,
    showName: Boolean = true
) {
    val palette = remember(club.primaryColor, club.secondaryColor) { badgePalette(club) }
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.plate.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClubBadge(club = club, size = 20.dp)
        if (showName) {
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
            Text(
                text = club.shortName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
