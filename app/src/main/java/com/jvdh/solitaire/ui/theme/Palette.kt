package com.jvdh.solitaire.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.jvdh.solitaire.data.CardBackColor
import com.jvdh.solitaire.data.TableTheme
import com.jvdh.solitaire.engine.Suit

/** The colours of the table itself, kept apart from the Material scheme. */
@Immutable
data class TableColors(
    val top: Color,
    val bottom: Color,
    val accent: Color,
    val onTable: Color,
    /** Outline of an empty pile position. */
    val slot: Color,
    /** Highlight drawn under a legal drop target. */
    val dropTarget: Color,
)

val LocalTableColors = staticCompositionLocalOf {
    TableTheme.FELT.colors(dark = false)
}

fun TableTheme.colors(dark: Boolean): TableColors = when (this) {
    TableTheme.FELT -> build(Color(0xFF1F6B45), Color(0xFF12452D), Color(0xFF4CC38A), dark)
    TableTheme.MIDNIGHT -> build(Color(0xFF1E2A44), Color(0xFF111726), Color(0xFF7AA2F7), dark)
    TableTheme.SLATE -> build(Color(0xFF3A4048), Color(0xFF23272D), Color(0xFF9FB3C8), dark)
    TableTheme.MAHOGANY -> build(Color(0xFF5E2A24), Color(0xFF361713), Color(0xFFE0A17A), dark)
    TableTheme.OCEAN -> build(Color(0xFF12545E), Color(0xFF07323A), Color(0xFF5FD0DE), dark)
    TableTheme.PLUM -> build(Color(0xFF4A2350), Color(0xFF2B1330), Color(0xFFCE93D8), dark)
    TableTheme.SAND -> build(Color(0xFFD8C7A6), Color(0xFFB8A27C), Color(0xFF7A5C2E), dark)
}

private fun build(top: Color, bottom: Color, accent: Color, dark: Boolean): TableColors {
    val light = top.luminanceIsLight()
    val onTable = if (light) Color(0xFF221C10) else Color(0xFFF2F5F3)
    return TableColors(
        top = if (dark) top.darken(0.18f) else top,
        bottom = if (dark) bottom.darken(0.25f) else bottom,
        accent = accent,
        onTable = onTable,
        slot = onTable.copy(alpha = 0.22f),
        dropTarget = accent.copy(alpha = 0.55f),
    )
}

/** Front and back colours of a card back design. */
fun CardBackColor.colors(): Pair<Color, Color> = when (this) {
    CardBackColor.RED -> Color(0xFFB3261E) to Color(0xFF7A1811)
    CardBackColor.BLUE -> Color(0xFF2B5FA8) to Color(0xFF1B3D6E)
    CardBackColor.GREEN -> Color(0xFF2E7D51) to Color(0xFF1B5134)
    CardBackColor.PURPLE -> Color(0xFF6A3E9E) to Color(0xFF452767)
    CardBackColor.CHARCOAL -> Color(0xFF41474D) to Color(0xFF262A2E)
    CardBackColor.AMBER -> Color(0xFFC77A16) to Color(0xFF8A530C)
}

/** Two-colour or four-colour suit palette. */
fun suitColor(suit: Suit, fourColour: Boolean): Color = when {
    !fourColour && suit.isRed -> Color(0xFFC62828)
    !fourColour -> Color(0xFF17191C)
    suit == Suit.HEARTS -> Color(0xFFC62828)
    suit == Suit.DIAMONDS -> Color(0xFF1565C0)
    suit == Suit.CLUBS -> Color(0xFF2E7D32)
    else -> Color(0xFF17191C)
}

private fun Color.luminanceIsLight(): Boolean = (red * 0.299f + green * 0.587f + blue * 0.114f) > 0.55f

private fun Color.darken(amount: Float): Color =
    Color(red * (1 - amount), green * (1 - amount), blue * (1 - amount), alpha)
