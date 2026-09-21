package com.jvdh.solitaire.board

import com.jvdh.solitaire.engine.Card
import com.jvdh.solitaire.engine.Fan
import com.jvdh.solitaire.engine.GameState
import com.jvdh.solitaire.engine.SolitaireGame

/** Cards are the usual poker proportion, near enough. */
const val CARD_ASPECT = 1.45f

/** How far a right-hand fan (Klondike's waste) spreads each card. */
private const val RIGHT_FAN_STEP = 0.32f

data class BoardRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom

    /** Area shared with [other]; zero when they do not touch. */
    fun overlap(other: BoardRect): Float {
        val w = minOf(right, other.right) - maxOf(left, other.left)
        val h = minOf(bottom, other.bottom) - maxOf(top, other.top)
        return if (w <= 0f || h <= 0f) 0f else w * h
    }
}

/** One card, placed in pixels, ready to draw. */
data class CardPlacement(
    val card: Card,
    val pile: Int,
    val index: Int,
    val faceUp: Boolean,
    val x: Float,
    val y: Float,
    /** Draw order; higher is nearer the player. */
    val z: Int,
) {
    fun rect(width: Float, height: Float) = BoardRect(x, y, x + width, y + height)
}

/** Everything the renderer and the gesture handler need for one frame. */
data class BoardGeometry(
    val cardWidth: Float,
    val cardHeight: Float,
    val placements: List<CardPlacement>,
    /** Where each pile starts, whether or not it holds cards. */
    val slotRects: Map<Int, BoardRect>,
) {
    /**
     * The card under [x], [y]. [placements] is already in draw order, so walking
     * it backwards tests from the front: a tap on the exposed sliver of a buried
     * card picks that card rather than the one lying over it.
     */
    fun cardAt(x: Float, y: Float): CardPlacement? =
        placements.asReversed().firstOrNull {
            it.rect(cardWidth, cardHeight).contains(x, y)
        }

    /** The pile under [x], [y], including empty ones. */
    fun pileAt(x: Float, y: Float): Int? {
        cardAt(x, y)?.let { return it.pile }
        return slotRects.entries.firstOrNull { it.value.contains(x, y) }?.key
    }

    /** Where a card added to [pile] would land, used to aim a drop. */
    fun landingRect(pile: Int): BoardRect {
        val top = placements.lastOrNull { it.pile == pile }
        return top?.rect(cardWidth, cardHeight) ?: slotRects[pile] ?: BoardRect(0f, 0f, 0f, 0f)
    }

    /** Piles ordered by how much a card released at [dragged] covers them. */
    fun dropCandidates(dragged: BoardRect): List<Int> =
        slotRects.keys
            .map { it to landingRect(it).overlap(dragged) }
            .filter { it.second > 0f }
            .sortedByDescending { it.second }
            .map { it.first }
}

/**
 * Lays the table out for the space available.
 *
 * Card size is whichever of width and height runs out first, so the whole
 * figure always fits; [scale] then shrinks it further if the player prefers
 * smaller cards. Columns that grow past the bottom are squeezed rather than
 * allowed to run off the screen.
 */
fun computeGeometry(
    game: SolitaireGame,
    state: GameState,
    containerWidth: Float,
    containerHeight: Float,
    faceUpStep: Float,
    faceDownStep: Float,
    scale: Float = 1f,
    leftHanded: Boolean = false,
): BoardGeometry {
    val layout = game.layout
    if (containerWidth <= 0f || containerHeight <= 0f) {
        return BoardGeometry(0f, 0f, emptyList(), emptyMap())
    }
    val byWidth = containerWidth / layout.width
    val byHeight = containerHeight / (layout.height * CARD_ASPECT)
    val cardWidth = minOf(byWidth, byHeight) * scale
    val cardHeight = cardWidth * CARD_ASPECT
    val originX = (containerWidth - layout.width * cardWidth) / 2f
    val originY = ((containerHeight - layout.height * cardHeight) / 2f).coerceAtLeast(0f) * 0.4f

    val placements = ArrayList<CardPlacement>()
    val slotRects = LinkedHashMap<Int, BoardRect>()
    var z = 0

    for (slot in layout.slots) {
        val pile = state.piles.getOrNull(slot.pile) ?: continue
        val slotX = if (leftHanded) layout.width - 1f - slot.x else slot.x
        val baseX = originX + slotX * cardWidth
        val baseY = originY + slot.y * cardHeight
        slotRects[slot.pile] = BoardRect(baseX, baseY, baseX + cardWidth, baseY + cardHeight)

        when (slot.fan) {
            Fan.NONE -> {
                for (index in pile.cards.indices) {
                    placements += CardPlacement(
                        card = pile.cards[index],
                        pile = slot.pile,
                        index = index,
                        faceUp = pile.isFaceUp(index),
                        x = baseX,
                        y = baseY,
                        z = z++,
                    )
                }
            }

            Fan.RIGHT -> {
                val limit = if (slot.fanLimit > 0) slot.fanLimit else pile.size
                val buried = (pile.size - limit).coerceAtLeast(0)
                for (index in pile.cards.indices) {
                    val step = (index - buried).coerceAtLeast(0)
                    placements += CardPlacement(
                        card = pile.cards[index],
                        pile = slot.pile,
                        index = index,
                        faceUp = pile.isFaceUp(index),
                        x = baseX + step * cardWidth * RIGHT_FAN_STEP,
                        y = baseY,
                        z = z++,
                    )
                }
            }

            Fan.DOWN -> {
                val up = faceUpStep * cardHeight
                val down = faceDownStep * cardHeight
                var needed = 0f
                for (index in 1 until pile.size) {
                    needed += if (pile.isFaceUp(index - 1)) up else down
                }
                val available = containerHeight - baseY - cardHeight
                val squeeze = if (needed > available && needed > 0f) {
                    (available / needed).coerceAtLeast(0.18f)
                } else {
                    1f
                }
                var y = baseY
                for (index in pile.cards.indices) {
                    if (index > 0) {
                        y += (if (pile.isFaceUp(index - 1)) up else down) * squeeze
                    }
                    placements += CardPlacement(
                        card = pile.cards[index],
                        pile = slot.pile,
                        index = index,
                        faceUp = pile.isFaceUp(index),
                        x = baseX,
                        y = y,
                        z = z++,
                    )
                }
            }
        }
    }
    return BoardGeometry(cardWidth, cardHeight, placements, slotRects)
}
