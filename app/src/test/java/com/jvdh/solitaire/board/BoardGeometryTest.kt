package com.jvdh.solitaire.board

import com.jvdh.solitaire.engine.GameId
import com.jvdh.solitaire.engine.GameOptions
import com.jvdh.solitaire.engine.PileKind
import com.jvdh.solitaire.engine.createGame
import com.jvdh.solitaire.engine.games.Klondike
import com.jvdh.solitaire.engine.games.TriPeaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** A phone-shaped viewport in pixels, landscape-ish board area. */
private const val WIDTH = 1080f
private const val HEIGHT = 1700f

class BoardGeometryTest {

    private fun geometry(
        id: GameId,
        width: Float = WIDTH,
        height: Float = HEIGHT,
        scale: Float = 1f,
        leftHanded: Boolean = false,
    ): Pair<BoardGeometry, com.jvdh.solitaire.engine.GameState> {
        val game = createGame(id, GameOptions())
        val state = game.deal(Random(9))
        return computeGeometry(
            game = game,
            state = state,
            containerWidth = width,
            containerHeight = height,
            faceUpStep = 0.26f,
            faceDownStep = 0.11f,
            scale = scale,
            leftHanded = leftHanded,
        ) to state
    }

    @Test
    fun `every game fits the viewport`() {
        for (id in GameId.entries) {
            val (geo, _) = geometry(id)
            assertTrue("$id has cards", geo.placements.isNotEmpty())
            val right = geo.placements.maxOf { it.x } + geo.cardWidth
            val left = geo.placements.minOf { it.x }
            assertTrue("$id does not run off the left edge", left >= -0.5f)
            assertTrue("$id does not run off the right edge", right <= WIDTH + 0.5f)
            val bottom = geo.placements.maxOf { it.y } + geo.cardHeight
            assertTrue("$id does not run off the bottom, was $bottom", bottom <= HEIGHT + 0.5f)
        }
    }

    @Test
    fun `every card is placed exactly once`() {
        for (id in GameId.entries) {
            val (geo, state) = geometry(id)
            val expected = state.piles.sumOf { it.size }
            assertEquals("$id places every card", expected, geo.placements.size)
            assertEquals(
                "$id has no duplicates",
                expected,
                geo.placements.map { it.card.code }.toSet().size,
            )
        }
    }

    @Test
    fun `a tap on the exposed sliver picks the buried card`() {
        val (geo, _) = geometry(GameId.KLONDIKE)
        val column = Klondike.TABLEAU + 6
        val cards = geo.placements.filter { it.pile == column }.sortedBy { it.index }
        assertEquals(7, cards.size)
        for (index in 0 until cards.size - 1) {
            val sliverY = (cards[index].y + cards[index + 1].y) / 2f
            val hit = geo.cardAt(cards[index].x + geo.cardWidth / 2f, sliverY)
            assertEquals("the sliver of card $index belongs to it", index, hit?.index)
        }
        val bottom = cards.last()
        val hit = geo.cardAt(bottom.x + geo.cardWidth / 2f, bottom.y + geo.cardHeight * 0.8f)
        assertEquals("the last card owns the rest of the column", cards.size - 1, hit?.index)
    }

    @Test
    fun `an empty pile is still a target`() {
        val game = createGame(GameId.KLONDIKE)
        var state = game.deal(Random(9))
        val stock = Klondike.STOCK
        state = state.with(stock to state[stock].take(0))
        val geo = computeGeometry(game, state, WIDTH, HEIGHT, 0.26f, 0.11f)
        val rect = geo.slotRects.getValue(stock)
        assertNull("nothing is drawn there", geo.cardAt(rect.centerX, rect.centerY))
        assertEquals("but the pile can still be tapped", stock, geo.pileAt(rect.centerX, rect.centerY))
    }

    @Test
    fun `a dragged card prefers the pile it covers most`() {
        val (geo, _) = geometry(GameId.KLONDIKE)
        val first = geo.slotRects.getValue(Klondike.TABLEAU)
        val nudged = BoardRect(
            first.left + geo.cardWidth * 0.2f,
            first.top,
            first.right + geo.cardWidth * 0.2f,
            first.bottom,
        )
        val candidates = geo.dropCandidates(nudged)
        assertEquals("the pile under most of the card wins", Klondike.TABLEAU, candidates.first())
        assertTrue("the neighbour is still offered", candidates.contains(Klondike.TABLEAU + 1))
        assertTrue("distant piles are not", !candidates.contains(Klondike.TABLEAU + 4))
    }

    @Test
    fun `a drop aims at the top of a column, not its head`() {
        val (geo, _) = geometry(GameId.KLONDIKE)
        val column = Klondike.TABLEAU + 6
        val landing = geo.landingRect(column)
        val slot = geo.slotRects.getValue(column)
        assertTrue("the column has grown downwards", landing.top > slot.top)
    }

    @Test
    fun `a long column is squeezed to stay on screen`() {
        val game = createGame(GameId.KLONDIKE)
        var state = game.deal(Random(9))
        val column = Klondike.TABLEAU
        val everything = state.piles.flatMap { it.cards }.take(24)
        state = state.with(
            column to com.jvdh.solitaire.engine.Pile(PileKind.TABLEAU, everything, faceUpFrom = 0),
        )
        val geo = computeGeometry(game, state, WIDTH, HEIGHT, 0.26f, 0.11f)
        val cards = geo.placements.filter { it.pile == column }
        val bottom = cards.maxOf { it.y } + geo.cardHeight
        assertTrue("24 cards still fit, was $bottom", bottom <= HEIGHT + 0.5f)
        assertTrue("and they are still readable", cards[1].y - cards[0].y > 4f)
    }

    @Test
    fun `left-handed mode mirrors the table`() {
        val (normal, _) = geometry(GameId.KLONDIKE)
        val (mirrored, _) = geometry(GameId.KLONDIKE, leftHanded = true)
        val stockNormal = normal.slotRects.getValue(Klondike.STOCK)
        val stockMirrored = mirrored.slotRects.getValue(Klondike.STOCK)
        assertTrue("the stock moves to the other side", stockMirrored.left > stockNormal.left)
        assertEquals(
            "the columns keep their spacing",
            normal.slotRects.getValue(Klondike.TABLEAU).width,
            mirrored.slotRects.getValue(Klondike.TABLEAU).width,
            0.01f,
        )
    }

    @Test
    fun `smaller cards still sit inside the board`() {
        val (small, _) = geometry(GameId.SPIDER, scale = 0.8f)
        val (full, _) = geometry(GameId.SPIDER, scale = 1f)
        assertTrue("the scale really shrinks them", small.cardWidth < full.cardWidth)
        assertTrue(small.placements.minOf { it.x } >= 0f)
        assertTrue(small.placements.maxOf { it.x } + small.cardWidth <= WIDTH)
    }

    @Test
    fun `the peaks are drawn from the back forwards`() {
        val (geo, _) = geometry(GameId.TRIPEAKS)
        val peak = geo.placements.first { it.pile == TriPeaks.TABLEAU }
        val base = geo.placements.first { it.pile == TriPeaks.TABLEAU + 18 }
        assertTrue("the base row covers the peaks", base.z > peak.z)
        assertTrue("and sits lower on the table", base.y > peak.y)
    }

    @Test
    fun `the waste shows only the last few cards`() {
        val game = createGame(GameId.KLONDIKE, GameOptions(klondikeDraw = 3))
        var state = game.deal(Random(9))
        repeat(4) { state = game.apply(state, com.jvdh.solitaire.engine.Move.Draw)!! }
        val geo = computeGeometry(game, state, WIDTH, HEIGHT, 0.26f, 0.11f)
        val waste = geo.placements.filter { it.pile == Klondike.WASTE }.sortedBy { it.index }
        assertEquals(12, waste.size)
        val spread = waste.map { it.x }.toSortedSet()
        assertEquals("a three-card fan, however deep the pile", 3, spread.size)
        assertNotNull(geo.cardAt(waste.last().x + 1f, waste.last().y + 1f))
    }

    @Test
    fun `a zero-sized board does not crash`() {
        val game = createGame(GameId.PYRAMID)
        val state = game.deal(Random(1))
        val geo = computeGeometry(game, state, 0f, 0f, 0.26f, 0.11f)
        assertTrue(geo.placements.isEmpty())
        assertNull(geo.pileAt(0f, 0f))
    }
}
