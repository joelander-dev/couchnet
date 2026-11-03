package com.local.firetvremote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.local.firetvremote.ThemeMode

/** Colors that change between light and dark mode. */
@Immutable
data class Palette(
    val ink: Color,
    val remoteBody: Color,
    val keyRest: Color,
    val pad: Color,
    val mist: Color,
    val name: Color,
    val danger: Color,
    val keyContent: Color,
    val accentContent: Color,
    val hairline: Color,
    val statusIdle: Color,
)

private val DarkPalette = Palette(
    ink = Color(0xFF09090B),
    remoteBody = Color(0xFF141418),
    keyRest = Color(0xFF2A2A32),
    pad = Color(0xFF1C1C22),
    mist = Color(0xFF9C9CA8),
    name = Color(0xFFF7F7F8),
    danger = Color(0xFFFFB4AB),
    keyContent = Color.White,
    accentContent = Color(0xFFFF9900),
    hairline = Color.White.copy(alpha = 0.06f),
    statusIdle = Color(0xFF6E6E78),
)

private val LightPalette = Palette(
    ink = Color(0xFFF6F5F2),
    remoteBody = Color(0xFFFFFFFF),
    keyRest = Color(0xFFECEBE7),
    pad = Color(0xFFE4E2DD),
    mist = Color(0xFF5E5D66),
    name = Color(0xFF141418),
    danger = Color(0xFFB3261E),
    keyContent = Color(0xFF1C1C21),
    // The bright amber is too faint for text and icons on white.
    accentContent = Color(0xFF9A5B00),
    hairline = Color.Black.copy(alpha = 0.08f),
    statusIdle = Color(0xFF9C9CA8),
)

private val LocalPalette = staticCompositionLocalOf { DarkPalette }

// Same in both modes: the pressed-key and filled-button orange.
val KeyDown = Color(0xFFFF9900)
val Amber = Color(0xFFFF9900)
val OnAmber = Color(0xFF1A1200)

val Ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val RemoteBody: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.remoteBody
val KeyRest: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.keyRest
val Pad: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.pad
val Mist: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.mist
val Name: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.name
val Danger: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.danger
val KeyContent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.keyContent
val AccentContent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accentContent
val Hairline: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.hairline
val StatusIdle: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.statusIdle

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FireRemoteTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val colors = if (dark) {
        darkColorScheme(
            primary = Amber,
            onPrimary = OnAmber,
            background = palette.ink,
            surface = palette.remoteBody,
            onBackground = palette.name,
            onSurface = palette.name,
            onSurfaceVariant = palette.mist,
            error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = palette.accentContent,
            onPrimary = Color.White,
            background = palette.ink,
            surface = palette.remoteBody,
            onBackground = palette.name,
            onSurface = palette.name,
            onSurfaceVariant = palette.mist,
            error = palette.danger,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
