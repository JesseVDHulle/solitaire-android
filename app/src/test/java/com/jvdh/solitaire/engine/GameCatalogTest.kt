package com.jvdh.solitaire.engine

import com.jvdh.solitaire.engine.games.Pyramid
import com.jvdh.solitaire.engine.games.ScoreMode
import com.jvdh.solitaire.engine.games.TriPeaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Settings really reach the rules, and the fixed figures really open up. */
class GameCatalogTest {

    @Test
    fun `options reach the game they belong to`() {
        val options = GameOptions(
            klondikeDraw = 3,
            klondikeRedeals = 1,
            klondikeScoring = ScoreMode.VEGAS,
            spiderSuits = 2,
            freeCellCells = 3,
            triPeaksWrap = false,
            pyramidRedeals = 0,
        )
        assertTrue(createGame(GameId.KLONDIKE, options).variantLabel.contains("Draw 3"))
        assertTrue(createGame(GameId.KLONDIKE, options).variantLabel.contains("1 redeals"))
        assertTrue(createGame(GameId.SPIDER, options).variantLabel.contains("2 suits"))
        assertEquals("3 free cells", createGame(GameId.FREECELL, options).variantLabel)
        assertTrue(createGame(GameId.TRIPEAKS, options).variantLabel.contains("No wrap"))
        assertEquals("One pass", createGame(GameId.PYRAMID, options).variantLabel)
    }

    @Test
    fun `fewer free cells really means fewer cells`() {
        for (cells in 2..4) {
            val game = createGame(GameId.FREECELL, GameOptions(freeCellCells = cells))
            val state = game.deal(Random(1))
            assertEquals(cells, state.indicesOf(PileKind.FREE_CELL).size)
            assertEquals(4, state.indicesOf(PileKind.FOUNDATION).size)
            assertEquals(8, state.indicesOf(PileKind.TABLEAU).size)
        }
    }

    @Test
    fun `vegas starts in the red and standard does not`() {
        val vegas = createGame(GameId.KLONDIKE, GameOptions(klondikeScoring = ScoreMode.VEGAS))
        val standard = createGame(GameId.KLONDIKE, GameOptions(klondikeScoring = ScoreMode.STANDARD))
        assertEquals(-52, vegas.deal(Random(1)).score)
        assertEquals(0, standard.deal(Random(1)).score)
    }

    @Test
    fun `every game has a blurb`() {
        for (id in GameId.entries) {
            assertTrue(id.name, blurbFor(id).isNotBlank())
            assertTrue(id.name, createGame(id).variantLabel.isNotBlank())
        }
    }

    @Test
    fun `clearing the tripeaks base opens the row above it, and so on`() {
        val game = TriPeaks()
        var state = game.deal(Random(4))
        // Rows are 3, 6, 9 and 10 cards; clear them from the bottom upwards.
        val rows = listOf(18 until 28, 9 until 18, 3 until 9, 0 until 3)
        for (row in rows) {
            for (slot in row) {
                assertTrue("slot $slot should be open", game.isOpen(state, slot))
                state = state.with(TriPeaks.TABLEAU + slot to Pile(PileKind.RESERVE))
            }
        }
        assertTrue("the peaks are cleared", game.isWon(state))
    }

    @Test
    fun `clearing a pyramid row opens the row above it`() {
        val game = Pyramid()
        var state = game.deal(Random(4))
        var start = 21
        for (row in 6 downTo 0) {
            for (offset in 0..row) {
                val slot = start + offset
                assertTrue("slot $slot should be open", game.isOpen(state, slot))
                state = state.with(Pyramid.PYRAMID + slot to Pile(PileKind.RESERVE))
            }
            start -= row
        }
        assertTrue("the pyramid is cleared", game.isWon(state))
    }

    @Test
    fun `a covered card stays out of reach until both its neighbours go`() {
        val game = Pyramid()
        var state = game.deal(Random(4))
        // Slot 15 is the head of row six, resting on slots 21 and 22.
        assertFalse(game.isOpen(state, 15))
        state = state.with(Pyramid.PYRAMID + 21 to Pile(PileKind.RESERVE))
        assertFalse("one neighbour is not enough", game.isOpen(state, 15))
        state = state.with(Pyramid.PYRAMID + 22 to Pile(PileKind.RESERVE))
        assertTrue("now it is reachable", game.isOpen(state, 15))
    }
}
