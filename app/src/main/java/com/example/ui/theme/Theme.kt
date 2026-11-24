package com.example.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = SaffronGold,
    onPrimary = Color(0xFF1A1205),
    primaryContainer = Color(0xFF422C06),
    onPrimaryContainer = SaffronGoldSoft,
    secondary = DamasceneCrimson,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4A1212),
    onSecondaryContainer = DamasceneCrimsonSoft,
    tertiary = EmeraldSuccess,
    onTertiary = Color(0xFF04291D),
    background = ObsidianBg,
    onBackground = Color(0xFFF6F1EA),
    surface = ObsidianSurface,
    onSurface = Color(0xFFF6F1EA),
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = DarkMutedText,
    outline = Color(0xFF3E352E)
)

private val LightColorScheme = lightColorScheme(
    primary = DamasceneCrimson,
    onPrimary = Color.White,
    primaryContainer = DamasceneCrimsonSoft,
    onPrimaryContainer = DamasceneCrimsonDark,
    secondary = SaffronGoldDark,
    onSecondary = Color.White,
    secondaryContainer = SaffronGoldSoft,
    onSecondaryContainer = Color(0xFF452400),
    tertiary = EmeraldSuccess,
    onTertiary = Color.White,
    background = WarmSandBg,
    onBackground = SlateText,
    surface = WarmSandSurface,
    onSurface = SlateText,
    surfaceVariant = WarmSandContainer,
    onSurfaceVariant = MutedText,
    outline = Color(0xFFE2D8CB)
)

val GhazaloShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun GhazaloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
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
        typography = GhazaloTypography,
        shapes = GhazaloShapes,
        content = content
    )
}
