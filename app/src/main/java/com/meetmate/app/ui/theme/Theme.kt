package com.meetmate.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Iris,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = IrisDeep,
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4DE),
    onSecondaryContainer = Color(0xFF5C1A10),
    tertiary = Success,
    background = Paper,
    onBackground = Ink,
    surface = PaperRaised,
    onSurface = Ink,
    surfaceVariant = PaperSunken,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = PaperRaised,
    surfaceContainerLow = PaperRaised,
    surfaceContainer = PaperRaised,
    surfaceContainerHigh = PaperRaised,
    surfaceContainerHighest = PaperSunken,
    outline = Color(0xFFC7C7D9),
    outlineVariant = Hairline,
    error = Danger,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = IrisSoft,
    onPrimary = IrisDeep,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = CoralSoft,
    onSecondary = Color(0xFF5C1A10),
    secondaryContainer = Color(0xFF7A2A1E),
    onSecondaryContainer = Color(0xFFFFE4DE),
    tertiary = SuccessDark,
    background = Night,
    onBackground = Mist,
    surface = NightRaised,
    onSurface = Mist,
    surfaceVariant = NightSunken,
    onSurfaceVariant = MistMuted,
    surfaceContainerLowest = Night,
    surfaceContainerLow = NightRaised,
    surfaceContainer = NightRaised,
    surfaceContainerHigh = NightSunken,
    surfaceContainerHighest = NightSunken,
    outline = Color(0xFF4A4A66),
    outlineVariant = NightHairline,
    error = DangerDark,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA)
)

val MeetMateShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/** Colours that Material's scheme has no slot for. */
@Immutable
data class MeetMateExtraColors(
    val success: Color,
    val danger: Color,
    val accent: Color
)

val LocalExtraColors = staticCompositionLocalOf {
    MeetMateExtraColors(success = Success, danger = Danger, accent = Coral)
}

@Composable
fun MeetMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default so the brand palette shows; wallpaper colours made every screen look generic.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val extraColors = if (darkTheme) {
        MeetMateExtraColors(success = SuccessDark, danger = DangerDark, accent = CoralSoft)
    } else {
        MeetMateExtraColors(success = Success, danger = Danger, accent = Coral)
    }

    CompositionLocalProvider(LocalExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = MeetMateShapes,
            content = content
        )
    }
}

object MeetMateTheme {
    val extraColors: MeetMateExtraColors
        @Composable get() = LocalExtraColors.current
}
