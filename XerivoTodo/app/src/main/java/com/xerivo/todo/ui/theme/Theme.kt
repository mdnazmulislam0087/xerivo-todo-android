package com.xerivo.todo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MintPulse,
    onPrimary = DeepInk,
    secondary = CalmBlue,
    onSecondary = DeepInk,
    tertiary = CitrusSpark,
    background = DeepInk,
    onBackground = CloudWhite,
    surface = NightBlue,
    onSurface = CloudWhite,
    surfaceVariant = CardBlue,
    onSurfaceVariant = CloudWhite.copy(alpha = 0.8f),
    error = AlertCoral
)

private val LightColorScheme = lightColorScheme(
    primary = CalmBlue,
    onPrimary = CloudWhite,
    secondary = MintPulse,
    onSecondary = DeepInk,
    tertiary = AlertCoral,
    background = CloudWhite,
    onBackground = InkText,
    surface = MistyBlue,
    onSurface = InkText,
    surfaceVariant = CloudWhite,
    onSurfaceVariant = InkText.copy(alpha = 0.8f),
    error = AlertCoral
)

@Composable
fun XerivoTodoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
