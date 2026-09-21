package com.jvdh.solitaire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvdh.solitaire.board.CARD_ASPECT
import com.jvdh.solitaire.data.CardBackStyle
import com.jvdh.solitaire.data.FaceStyle
import com.jvdh.solitaire.data.Settings
import com.jvdh.solitaire.engine.Card
import com.jvdh.solitaire.ui.theme.colors
import com.jvdh.solitaire.ui.theme.suitColor

fun cardCorner(width: Dp): Dp = width * 0.09f

/**
 * One card, face up or face down. Sizes everything from [width] so the same
 * composable works at any scale without a second set of dimensions.
 */
@Composable
fun CardView(
    card: Card?,
    faceUp: Boolean,
    width: Dp,
    settings: Settings,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    hinted: Boolean = false,
    accent: Color = Color.Transparent,
) {
    val height = width * CARD_ASPECT
    val shape = RoundedCornerShape(cardCorner(width))
    val outline = when {
        selected -> accent
        hinted -> accent.copy(alpha = 0.8f)
        else -> Color(0x33000000)
    }
    Box(
        modifier = modifier
            .size(width, height)
            .clip(shape)
            .background(if (faceUp) Color(0xFFFCFCFA) else Color.Transparent)
            .border(if (selected || hinted) width * 0.05f else width * 0.012f, outline, shape),
    ) {
        if (card == null) return@Box
        if (faceUp) CardFace(card, width, settings) else CardBack(width, settings)
    }
}

@Composable
private fun BoxScope.CardFace(card: Card, width: Dp, settings: Settings) {
    val colour = suitColor(card.suit, settings.fourColourDeck)
    val rank = card.rankLabel
    val symbol = card.suit.symbol
    when (settings.faceStyle) {
        FaceStyle.CLASSIC -> {
            CornerIndex(rank, symbol, colour, width)
            Text(
                text = symbol,
                color = colour.copy(alpha = 0.92f),
                fontSize = (width.value * 0.46f).sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        FaceStyle.BOLD -> {
            Text(
                text = rank,
                color = colour,
                fontSize = (width.value * 0.62f).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            Text(
                text = symbol,
                color = colour,
                fontSize = (width.value * 0.26f).sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(width * 0.08f),
            )
        }

        FaceStyle.MINIMAL -> {
            Text(
                text = rank + symbol,
                color = colour,
                fontSize = (width.value * 0.30f).sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = width * 0.10f, top = width * 0.07f),
            )
        }
    }
}

@Composable
private fun CornerIndex(rank: String, symbol: String, colour: Color, width: Dp) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(start = width * 0.08f, top = width * 0.05f)
            .size(width * 0.30f, width * 0.52f),
    ) {
        Text(
            text = rank,
            color = colour,
            fontSize = (width.value * 0.28f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = symbol,
            color = colour,
            fontSize = (width.value * 0.22f).sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun CardBack(width: Dp, settings: Settings) {
    val (light, dark) = settings.cardBackColor.colors()
    val inset = width * 0.07f
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(dark)
            .padding(inset),
    ) {
        drawRect(light)
        clipRect {
            drawBackPattern(settings.cardBackStyle, light, dark)
        }
    }
}

/** The repeating design on the reverse of a card. */
private fun DrawScope.drawBackPattern(style: CardBackStyle, light: Color, dark: Color) {
    val ink = dark.copy(alpha = 0.55f)
    val w = size.width
    val h = size.height
    when (style) {
        CardBackStyle.SOLID -> Unit

        CardBackStyle.WEAVE -> {
            val step = w / 5f
            val stroke = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
            var offset = -h
            while (offset < w + h) {
                drawLine(ink, Offset(offset, 0f), Offset(offset + h, h), stroke.width, stroke.cap)
                drawLine(ink, Offset(offset + h, 0f), Offset(offset, h), stroke.width, stroke.cap)
                offset += step
            }
        }

        CardBackStyle.DIAMONDS -> {
            val cols = 4
            val rows = 6
            val cw = w / cols
            val ch = h / rows
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val cx = cw * (col + 0.5f)
                    val cy = ch * (row + 0.5f)
                    val rx = cw * 0.34f
                    val ry = ch * 0.34f
                    val path = Path().apply {
                        moveTo(cx, cy - ry)
                        lineTo(cx + rx, cy)
                        lineTo(cx, cy + ry)
                        lineTo(cx - rx, cy)
                        close()
                    }
                    drawPath(path, if ((row + col) % 2 == 0) ink else light)
                }
            }
        }

        CardBackStyle.DOTS -> {
            val cols = 5
            val rows = 8
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val shift = if (row % 2 == 0) 0f else w / (cols * 2f)
                    drawCircle(
                        color = ink,
                        radius = w * 0.045f,
                        center = Offset(w * (col + 0.5f) / cols + shift, h * (row + 0.5f) / rows),
                    )
                }
            }
        }

        CardBackStyle.WAVES -> {
            val rows = 9
            val stroke = w * 0.035f
            for (row in 0..rows) {
                val y = h * row / rows
                val path = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    val segment = w / 4f
                    var up = true
                    while (x < w) {
                        val next = (x + segment).coerceAtMost(w)
                        quadraticBezierTo(
                            (x + next) / 2f,
                            if (up) y - h * 0.035f else y + h * 0.035f,
                            next,
                            y,
                        )
                        up = !up
                        x = next
                    }
                }
                drawPath(path, ink, style = Stroke(width = stroke))
            }
        }

        CardBackStyle.GRID -> {
            val stroke = w * 0.03f
            val cols = 4
            val rows = 6
            for (col in 0..cols) {
                val x = w * col / cols
                drawLine(ink, Offset(x, 0f), Offset(x, h), stroke)
            }
            for (row in 0..rows) {
                val y = h * row / rows
                drawLine(ink, Offset(0f, y), Offset(w, y), stroke)
            }
        }
    }
}

/** The outline shown where a pile lives when it has no cards. */
@Composable
fun EmptySlot(
    width: Dp,
    stroke: Color,
    modifier: Modifier = Modifier,
    fill: Color = Color.Transparent,
    glyph: String? = null,
) {
    val shape = RoundedCornerShape(cardCorner(width))
    Box(
        modifier = modifier
            .size(width, width * CARD_ASPECT)
            .clip(shape)
            .background(fill)
            .border(width * 0.02f, stroke, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) {
            Text(
                text = glyph,
                color = stroke,
                fontSize = (width.value * 0.34f).sp,
            )
        }
    }
}

