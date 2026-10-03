package com.footymanager.simulator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.footymanager.simulator.domain.model.Club
import com.footymanager.simulator.domain.model.Stadium

/**
 * A stylised, top-down illustration of the club's ground.
 *
 * It is drawn entirely with Canvas primitives - no image assets - and reflects
 * real game state: the number of stand rings grows with the stadium level and
 * the pitch is tinted in the club's colours. This gives the stadium screen a
 * strong visual anchor without shipping any artwork.
 */
@Composable
fun StadiumIllustration(
    club: Club,
    stadium: Stadium,
    modifier: Modifier = Modifier
) {
    val palette = badgePalette(club)
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val pitchColor = blend(Color(0xFF1E7A3C), palette.plate, 0.35f)
    val lineColor = Color.White.copy(alpha = 0.75f)
    val levelFraction by animateFloatAsState(
        targetValue = (stadium.level - 1) / (Stadium.MAX_CAPACITY.size - 1f).coerceAtLeast(1f),
        animationSpec = tween(durationMillis = 700),
        label = "stadiumLevel"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.5f)) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f

                // Outer stand rings, one per upgrade level.
                val rings = 2 + (levelFraction * 2f).toInt().coerceIn(0, 2)
                for (ring in rings downTo 1) {
                    val inset = ring * (w * 0.045f)
                    val topLeft = Offset(inset, inset * 0.75f)
                    val ringSize = Size(w - inset * 2f, h - inset * 1.5f)
                    drawRoundRect(
                        color = palette.plate.copy(alpha = 0.30f + 0.12f * (rings - ring)),
                        topLeft = topLeft,
                        size = ringSize,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.28f)
                    )
                    drawRoundRect(
                        color = palette.outline.copy(alpha = 0.55f),
                        topLeft = topLeft,
                        size = ringSize,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.28f),
                        style = Stroke(width = 2f)
                    )
                }

                // Pitch.
                val pitchInset = w * 0.20f
                val pitchTopLeft = Offset(pitchInset, pitchInset * 0.62f)
                val pitchSize = Size(w - pitchInset * 2f, h - pitchInset * 1.24f)
                drawRoundRect(
                    color = pitchColor,
                    topLeft = pitchTopLeft,
                    size = pitchSize,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f)
                )
                // Halfway line.
                drawLine(
                    color = lineColor,
                    start = Offset(cx, pitchTopLeft.y),
                    end = Offset(cx, pitchTopLeft.y + pitchSize.height),
                    strokeWidth = 2f
                )
                // Centre circle.
                drawCircle(
                    color = lineColor,
                    radius = pitchSize.height * 0.16f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
                // Penalty boxes.
                val boxW = pitchSize.width * 0.16f
                val boxH = pitchSize.height * 0.52f
                drawRect(
                    color = lineColor,
                    topLeft = Offset(pitchTopLeft.x, cy - boxH / 2f),
                    size = Size(boxW, boxH),
                    style = Stroke(width = 2f)
                )
                drawRect(
                    color = lineColor,
                    topLeft = Offset(pitchTopLeft.x + pitchSize.width - boxW, cy - boxH / 2f),
                    size = Size(boxW, boxH),
                    style = Stroke(width = 2f)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stadium.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Level ${stadium.level} · ${"%,d".format(stadium.capacity)} seats",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun blend(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f
)
