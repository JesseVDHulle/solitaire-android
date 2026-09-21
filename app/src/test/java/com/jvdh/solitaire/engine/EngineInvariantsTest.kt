package com.jvdh.solitaire.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Rules that must hold for every game: cards are conserved, illegal moves are
 * refused, and playing by the engine's own hints never corrupts a deal.
 */
class EngineInvariantsTest {

    private val variants: List<Pair<String, SolitaireGame>> = listOf(
        "klondike draw 1" to createGame(GameId.KLONDIKE, GameOptions(klondikeDraw = 1)),
        "klondike draw 3" to createGame(GameId.KLONDIKE, GameOptions(klondikeDraw = 3)),
        "spider 1 suit" to createGame(GameId.SPIDER, GameOptions(spiderSuits = 1)),
        "spider 2 suits" to createGame(GameId.SPIDER, GameOptions(spiderSuits = 2)),
        "spider 4 suits" to createGame(GameId.SPIDER, GameOptions(spiderSuits = 4)),
        "freecell" to createGame(GameId.FREECELL),
        "tripeaks" to createGame(GameId.TRIPEAKS),
        "pyramid" to createGame(GameId.PYRAMID),
    )

    private fun cardsOf(state: GameState): List<Int> =
        state.piles.flatMap { pile -> pile.cards.map { it.code } }

    @Test
    fun `every deal uses each card exactly once`() {
        for ((name, game) in variants) {
            for (seed in 0 until 20) {
                val state = game.deal(Random(seed.toLong()))
                val codes = cardsOf(state)
                val expected = if (game.id == GameId.SPIDER) 104 else 52
                assertEquals("$name deals $expected cards", expected, codes.size)
                assertEquals("$name has no duplicate cards", codes.size, codes.toSet().size)
            }
        }
    }

    @Test
    fun `every layout has a slot for every pile`() {
        for ((name, game) in variants) {
            val state = game.deal(Random(1))
            for (index in state.piles.indices) {
                assertNotNull("$name pile $index has a slot", game.layout.slotFor(index))
            }
            assertTrue("$name layout has width", game.layout.width > 0f)
            assertTrue("$name layout has height", game.layout.height > 0f)
        }
    }

    @Test
    fun `playing hints never loses or duplicates a card`() {
        for ((name, game) in variants) {
            for (seed in 0 until 12) {
                var state = game.deal(Random(seed.toLong()))
                val start = cardsOf(state).toSet()
                var steps = 0
                while (steps < 400 && !game.isWon(state)) {
                    val hint = game.hint(state) ?: break
                    val next = game.apply(state, hint)
                    if (next == null) {
                        // A hint the game will not accept is a bug in the hint.
                        throw AssertionError("$name seed $seed produced an illegal hint: $hint")
                    }
                    state = next
                    val codes = cardsOf(state)
                    assertEquals("$name seed $seed conserves cards", start.size, codes.size)
                    assertEquals("$name seed $seed has no duplicates", codes.size, codes.toSet().size)
                    steps++
                }
            }
        }
    }

    @Test
    fun `apply refuses moves that canDrop rejects`() {
        for ((name, game) in variants) {
            val state = game.deal(Random(7))
            for (from in state.piles.indices) {
                for (index in state[from].cards.indices) {
                    for (to in state.piles.indices) {
                        if (game.canDrop(state, from, index, to)) continue
                        val result = game.apply(state, Move.cards(from, index, to))
                        assertTrue(
                            "$name allowed an illegal move $from[$index] -> $to",
                            result == null,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `face-up marks stay inside the pile`() {
        for ((name, game) in variants) {
            var state = game.deal(Random(3))
            repeat(120) {
                val hint = game.hint(state) ?: return@repeat
                state = game.apply(state, hint) ?: return@repeat
                for (pile in state.piles) {
                    assertTrue(
                        "$name keeps faceUpFrom in range",
                        pile.faceUpFrom in 0..pile.size,
                    )
                }
            }
        }
    }

    @Test
    fun `saved games survive a round trip`() {
        for ((name, game) in variants) {
            var state = game.deal(Random(11))
            repeat(15) { state = game.hint(state)?.let { game.apply(state, it) } ?: state }
            val restored = StateCodec.decode(StateCodec.encode(state))
            assertEquals("$name round trips", state, restored)
        }
    }

    @Test
    fun `a corrupt save decodes to null rather than crashing`() {
        assertEquals(null, StateCodec.decode(""))
        assertEquals(null, StateCodec.decode("v2;0;0;0;0;"))
        assertEquals(null, StateCodec.decode("v1;0;0;0;0;99:0:1"))
        assertEquals(null, StateCodec.decode("v1;x;0;0;0;0:0:1"))
    }

    @Test
    fun `an empty pile encodes and decodes`() {
        val state = GameState(listOf(Pile(PileKind.STOCK), Pile(PileKind.WASTE, listOf(Card(0)))))
        assertEquals(state, StateCodec.decode(StateCodec.encode(state)))
    }

    @Test
    fun `picking up a card the game refuses is never droppable`() {
        for ((name, game) in variants) {
            val state = game.deal(Random(5))
            for (from in state.piles.indices) {
                for (index in state[from].cards.indices) {
                    if (game.canPickUp(state, from, index)) continue
                    for (to in state.piles.indices) {
                        assertFalse(
                            "$name offered a drop for an unliftable card",
                            game.canDrop(state, from, index, to),
                        )
                    }
                }
            }
        }
    }
}
