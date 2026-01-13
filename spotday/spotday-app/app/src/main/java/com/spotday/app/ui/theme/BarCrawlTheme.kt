package com.spotday.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Bar Crawl Mode Colors - Dark Neon Aesthetic
val BarCrawlBackground = Color(0xFF0D0221)      // Deep purple/black
val BarCrawlSurface = Color(0xFF1A0A2E)         // Slightly lighter purple
val BarCrawlPrimary = Color(0xFFFF2D95)         // Neon magenta/pink
val BarCrawlSecondary = Color(0xFFFFB800)       // Warm amber
val BarCrawlTertiary = Color(0xFF00F5FF)        // Electric cyan
val BarCrawlOnBackground = Color(0xFFFFFFFF)    // White text
val BarCrawlOnSurface = Color(0xFFE0E0E0)       // Light gray text
val BarCrawlOnPrimary = Color(0xFF000000)       // Black on neon pink
val BarCrawlSurfaceVariant = Color(0xFF2D1B4E)  // Purple surface variant
val BarCrawlOutline = Color(0xFF6B4D8A)         // Muted purple outline

private val BarCrawlColorScheme = darkColorScheme(
    primary = BarCrawlPrimary,
    onPrimary = BarCrawlOnPrimary,
    primaryContainer = Color(0xFF8B0A50),
    onPrimaryContainer = Color(0xFFFFD9E7),
    secondary = BarCrawlSecondary,
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF5C4200),
    onSecondaryContainer = Color(0xFFFFE08A),
    tertiary = BarCrawlTertiary,
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF004D54),
    onTertiaryContainer = Color(0xFFB8F5FF),
    background = BarCrawlBackground,
    onBackground = BarCrawlOnBackground,
    surface = BarCrawlSurface,
    onSurface = BarCrawlOnSurface,
    surfaceVariant = BarCrawlSurfaceVariant,
    onSurfaceVariant = Color(0xFFD0C0E0),
    outline = BarCrawlOutline,
    outlineVariant = Color(0xFF4A3660),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF000000)
)

@Composable
fun BarCrawlTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = BarCrawlColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = BarCrawlBackground.toArgb()
            window.navigationBarColor = BarCrawlBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
