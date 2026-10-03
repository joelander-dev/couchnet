package com.local.firetvremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF09090B)
val RemoteBody = Color(0xFF141418)
val KeyRest = Color(0xFF2A2A32)
val Pad = Color(0xFF1C1C22)
val KeyDown = Color(0xFFFF9900)
val Amber = Color(0xFFFF9900)
val Mist = Color(0xFF9C9CA8)
val Name = Color(0xFFF7F7F8)
val Danger = Color(0xFFFFB4AB)

private val Colors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1200),
    background = Ink,
    surface = RemoteBody,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Danger,
)

@Composable
fun FireRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
