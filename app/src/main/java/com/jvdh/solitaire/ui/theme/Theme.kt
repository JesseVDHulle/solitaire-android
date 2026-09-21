package com.jvdh.solitaire.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jvdh.solitaire.data.Settings
import com.jvdh.solitaire.data.ThemeMode

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

@Composable
fun SolitaireTheme(settings: Settings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
    val table = settings.tableTheme.colors(dark)
    val pureBlack = settings.themeMode == ThemeMode.BLACK

    val scheme = if (dark) {
        darkColorScheme(
            primary = table.accent,
            onPrimary = Color(0xFF10140F),
            secondary = table.accent,
            background = if (pureBlack) Color.Black else Color(0xFF14161A),
            onBackground = Color(0xFFE6E9EC),
            surface = if (pureBlack) Color.Black else Color(0xFF1B1E23),
            onSurface = Color(0xFFE6E9EC),
            surfaceVariant = if (pureBlack) Color(0xFF101013) else Color(0xFF262A30),
            onSurfaceVariant = Color(0xFFBFC6CE),
            outline = Color(0xFF4A5058),
        )
    } else {
        lightColorScheme(
            primary = table.accent,
            onPrimary = Color.White,
            secondary = table.accent,
            background = Color(0xFFF7F8F7),
            onBackground = Color(0xFF15181B),
            surface = Color.White,
            onSurface = Color(0xFF15181B),
            surfaceVariant = Color(0xFFE9ECEF),
            onSurfaceVariant = Color(0xFF4A5058),
            outline = Color(0xFFC3C9CF),
        )
    }

    CompositionLocalProvider(LocalTableColors provides table) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
