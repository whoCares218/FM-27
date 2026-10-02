package com.footymanager.simulator.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = GrassGreen,
    onPrimary = Color(0xFF04160C),
    primaryContainer = GrassGreenDark,
    onPrimaryContainer = Color(0xFFD6FBE4),
    secondary = TurfTeal,
    onSecondary = Color(0xFF04160C),
    secondaryContainer = Color(0xFF14453A),
    onSecondaryContainer = Color(0xFFD2F5EC),
    tertiary = Amber,
    onTertiary = Color(0xFF241800),
    background = PitchBackground,
    onBackground = TextPrimaryDark,
    surface = PitchSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = PitchSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = PitchSurfaceElevated,
    surfaceContainerHigh = PitchSurfaceVariant,
    surfaceContainerLow = PitchSurface,
    outline = PitchOutline,
    outlineVariant = Color(0xFF202C46),
    error = DangerRed,
    onError = Color(0xFF2A0507)
)

private val LightColors = lightColorScheme(
    primary = GrassGreenLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F2DA),
    onPrimaryContainer = Color(0xFF06301A),
    secondary = TurfTealLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6EEE3),
    onSecondaryContainer = Color(0xFF06312A),
    tertiary = AmberLight,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = LightSurfaceElevated,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerLow = LightSurface,
    outline = LightOutline,
    outlineVariant = Color(0xFFDCE3EF),
    error = DangerRedLight,
    onError = Color.White
)

private val FmShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Extra semantic tokens that Material's scheme does not model directly. */
data class FmExtendedColors(
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val info: Color,
    val accentViolet: Color,
    val isDark: Boolean
)

val LocalFmExtendedColors = staticCompositionLocalOf {
    FmExtendedColors(
        positive = StatColors.elite,
        negative = StatColors.bad,
        warning = StatColors.average,
        info = StatColors.poor,
        accentViolet = Violet,
        isDark = true
    )
}

@Composable
fun FootballManagerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val extended = if (darkTheme) {
        FmExtendedColors(
            positive = StatColors.elite,
            negative = StatColors.bad,
            warning = StatColors.average,
            info = InfoBlue,
            accentViolet = Violet,
            isDark = true
        )
    } else {
        FmExtendedColors(
            positive = StatColors.elite,
            negative = DangerRedLight,
            warning = AmberLight,
            info = InfoBlueLight,
            accentViolet = Violet,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalFmExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = FmTypography,
            shapes = FmShapes,
            content = content
        )
    }
}
