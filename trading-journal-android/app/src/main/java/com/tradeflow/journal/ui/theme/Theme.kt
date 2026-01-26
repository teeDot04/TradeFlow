package com.tradeflow.journal.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenTertiary,
    onPrimaryContainer = Color(0xFF002106),
    
    secondary = YellowAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE082),
    onSecondaryContainer = Color(0xFF2A1800),
    
    tertiary = GreenSecondary,
    onTertiary = Color.White,
    
    error = RedPrimary,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    
    background = LightBackground,
    onBackground = LightOnSurface,
    
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF43474E),
    
    outline = LightOutline,
    outlineVariant = Color(0xFFC7C9C8)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkGreenPrimary,
    onPrimary = Color(0xFF003910),
    primaryContainer = Color(0xFF005319),
    onPrimaryContainer = DarkGreenTertiary,
    
    secondary = DarkYellowAccent,
    onSecondary = Color(0xFF3E2E00),
    secondaryContainer = Color(0xFF5A4300),
    onSecondaryContainer = Color(0xFFFFE082),
    
    tertiary = DarkGreenSecondary,
    onTertiary = Color(0xFF003910),
    
    error = DarkRedPrimary,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    background = DarkBackground,
    onBackground = DarkOnSurface,
    
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB8BBBF),
    
    outline = DarkOutline,
    outlineVariant = Color(0xFF43474E)
)

@Composable
fun TradeFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
