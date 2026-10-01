package com.mumeinosato.musicplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Violet80,
    onPrimary = VioletDeep80,
    primaryContainer = VioletContainer80,
    onPrimaryContainer = Color.White,
    secondaryContainer = VioletMid80,
    tertiaryContainer = VioletDeep80,
    background = Night80,
    onBackground = OnNight80,
    surface = NightSurface80,
    onSurface = OnNight80,
    onSurfaceVariant = OnNightVariant80,
    surfaceContainer = NightTop80,
)

private val LightColorScheme = lightColorScheme(
    primary = Violet40,
    onPrimary = Color.White,
    primaryContainer = VioletContainer40,
    onPrimaryContainer = Color.White,
    secondaryContainer = VioletMid40,
    tertiaryContainer = VioletDeep40,
    background = Cloud40,
    onBackground = OnCloud40,
    surface = CloudSurface40,
    onSurface = OnCloud40,
    onSurfaceVariant = OnCloudVariant40,
    surfaceContainer = Color.White,
)

@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
