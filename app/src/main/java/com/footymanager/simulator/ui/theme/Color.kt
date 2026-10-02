package com.footymanager.simulator.ui.theme

import androidx.compose.ui.graphics.Color

// ---- Dark (primary) palette: modern sports-management look ----
val PitchBackground = Color(0xFF0B1220)
val PitchSurface = Color(0xFF141C2E)
val PitchSurfaceElevated = Color(0xFF1B2540)
val PitchSurfaceVariant = Color(0xFF23304D)
val PitchOutline = Color(0xFF2E3C5C)

val GrassGreen = Color(0xFF1FA55C)
val GrassGreenBright = Color(0xFF2FC26F)
val GrassGreenDark = Color(0xFF116B3A)
val TurfTeal = Color(0xFF2DD4A7)
val Amber = Color(0xFFF2B01E)
val DangerRed = Color(0xFFE5484D)
val InfoBlue = Color(0xFF4C8DFF)
val Violet = Color(0xFF8B7BFF)

val TextPrimaryDark = Color(0xFFE9EEF9)
val TextSecondaryDark = Color(0xFF9BA9C4)
val TextTertiaryDark = Color(0xFF6B7A96)

// ---- Light palette ----
val LightBackground = Color(0xFFF2F5FA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE4EAF4)
val LightOutline = Color(0xFFCBD5E5)

val GrassGreenLight = Color(0xFF127A44)
val GrassGreenBrightLight = Color(0xFF179152)
val TurfTealLight = Color(0xFF0E9B78)
val AmberLight = Color(0xFFB87A00)
val DangerRedLight = Color(0xFFC03034)
val InfoBlueLight = Color(0xFF2A5FD1)

val TextPrimaryLight = Color(0xFF101828)
val TextSecondaryLight = Color(0xFF4A5670)
val TextTertiaryLight = Color(0xFF78849C)

/** The pitch used by the tactics screen: a deep, low-glare turf. */
val PitchGreen = Color(0xFF0E3B24)

/** Semantic colours for ratings / morale / form. Shared by both themes. */
object StatColors {
    val elite = Color(0xFF2FC26F)
    val good = Color(0xFF7BD14A)
    val average = Color(0xFFF2B01E)
    val poor = Color(0xFFE58A2F)
    val bad = Color(0xFFE5484D)

    fun forRating(rating: Int): Color = when {
        rating >= 85 -> elite
        rating >= 78 -> good
        rating >= 70 -> average
        rating >= 62 -> poor
        else -> bad
    }

    fun forForm(form: Int): Color = when {
        form >= 8 -> elite
        form >= 6 -> good
        form >= 4 -> average
        form >= 2 -> poor
        else -> bad
    }
}
