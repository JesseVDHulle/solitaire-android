package com.jvdh.solitaire.engine

import com.jvdh.solitaire.engine.games.FreeCell
import com.jvdh.solitaire.engine.games.Klondike
import com.jvdh.solitaire.engine.games.Pyramid
import com.jvdh.solitaire.engine.games.Spider
import com.jvdh.solitaire.engine.games.TriPeaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Rules checked against hand-built positions, one game at a time. */
class GameRulesTest {

    private fun card(suit: Suit, rank: Int, uid: Int = rank * 4 + suit.ordinal) = Card(uid, suit, rank)

    // ---------------------------------------------------------------- Klondike

    @Test
    fun `klondike deals a staircase with one card face up per column`() {
        val game = Klondike()
        val state = game.deal(Random(1))
        for (column in 0 until 7) {
            val pile = state[Klondike.TABLEAU + column]
            assertEquals(column + 1, pile.size)
            assertEquals("one card showing", column, pile.faceUpFrom)
        }
        assertEquals(24, state[Klondike.STOCK].size)
        assertEquals(0, state[Klondike.WASTE].size)
    }

    @Test
    fun `klondike tableau takes a lower card of the other colour only`() {
        val game = Klondike()
        val state = klondikeWith(
            tableau0 = listOf(card(Suit.SPADES, 7)),
            tableau1 = listOf(card(Suit.HEARTS, 6)),
            tableau2 = listOf(card(Suit.DIAMONDS, 6)),
            tableau3 = listOf(card(Suit.CLUBS, 6)),
        )
        val t = Klondike.TABLEAU
        assertTrue("red six onto black seven", game.canDrop(state, t + 1, 0, t))
        assertTrue("other red six onto black seven", game.canDrop(state, t + 2, 0, t))
        assertFalse("black six onto black seven", game.canDrop(state, t + 3, 0, t))
    }

    @Test
    fun `klondike only a king starts an empty column`() {
        val game = Klondike()
        val state = klondikeWith(
            tableau0 = emptyList(),
            tableau1 = listOf(card(Suit.HEARTS, KING)),
            tableau2 = listOf(card(Suit.CLUBS, QUEEN)),
        )
        val t = Klondike.TABLEAU
        assertTrue(game.canDrop(state, t + 1, 0, t))
        assertFalse(game.canDrop(state, t + 2, 0, t))
    }

    @Test
    fun `klondike foundations build up in suit from the ace`() {
        val game = Klondike()
        val state = klondikeWith(
            tableau0 = listOf(card(Suit.SPADES, ACE)),
            tableau1 = listOf(card(Suit.SPADES, 2)),
            tableau2 = listOf(card(Suit.HEARTS, 2)),
        )
        val t = Klondike.TABLEAU
        val f = Klondike.FOUNDATION
        assertTrue("ace opens the foundation", game.canDrop(state, t, 0, f))
        assertFalse("a two cannot open it", game.canDrop(state, t + 1, 0, f))
        val afterAce = game.apply(state, Move.cards(t, 0, f))!!
        assertTrue("two of the same suit follows", game.canDrop(afterAce, t + 1, 0, f))
        assertFalse("a different suit does not", game.canDrop(afterAce, t + 2, 0, f))
    }

    @Test
    fun `klondike moves a run but not a broken sequence`() {
        val game = Klondike()
        val run = listOf(card(Suit.SPADES, 5), card(Suit.HEARTS, 4), card(Suit.CLUBS, 3))
        val broken = listOf(card(Suit.SPADES, 5), card(Suit.CLUBS, 4))
        val state = klondikeWith(
            tableau0 = listOf(card(Suit.DIAMONDS, 6)),
            tableau1 = run,
            tableau2 = broken,
        )
        val t = Klondike.TABLEAU
        assertTrue("the whole alternating run travels", game.canDrop(state, t + 1, 0, t))
        assertFalse("a same-colour pair does not", game.canDrop(state, t + 2, 0, t))
        val moved = game.apply(state, Move.cards(t + 1, 0, t))!!
        assertEquals(4, moved[t].size)
        assertEquals(0, moved[t + 1].size)
    }

    @Test
    fun `klondike turns over the card it uncovers`() {
        val game = Klondike()
        val hidden = card(Suit.CLUBS, 9)
        val state = klondikeWith(
            tableau0 = listOf(card(Suit.DIAMONDS, 6)),
            tableau1 = listOf(hidden, card(Suit.SPADES, 5)),
            tableau1FaceUpFrom = 1,
        )
        val t = Klondike.TABLEAU
        val moved = game.apply(state, Move.cards(t + 1, 1, t))!!
        assertEquals(0, moved[t + 1].faceUpFrom)
        assertTrue("uncovering pays five", moved.score >= 5)
    }

    @Test
    fun `klondike draw three turns three and reverses them`() {
        val game = Klondike(drawCount = 3)
        val state = game.deal(Random(4))
        val top = state[Klondike.STOCK].cards.takeLast(3)
        val drawn = game.apply(state, Move.Draw)!!
        assertEquals(21, drawn[Klondike.STOCK].size)
        assertEquals(3, drawn[Klondike.WASTE].size)
        assertEquals("the deepest of the three ends on top", top.first(), drawn[Klondike.WASTE].top)
    }

    @Test
    fun `klondike recycles only when the stock is empty and a redeal is left`() {
        val game = Klondike(drawCount = 1, redeals = 1)
        var state = game.deal(Random(2))
        assertNull("cannot recycle with cards left", game.apply(state, Move.Recycle))
        repeat(24) { state = game.apply(state, Move.Draw)!! }
        assertEquals(0, state[Klondike.STOCK].size)
        state = game.apply(state, Move.Recycle)!!
        assertEquals(24, state[Klondike.STOCK].size)
        assertEquals(1, state.passes)
        repeat(24) { state = game.apply(state, Move.Draw)!! }
        assertNull("the single redeal is spent", game.apply(state, Move.Recycle))
        assertFalse(game.canDraw(state))
    }

    @Test
    fun `klondike tap sends a card home before moving it sideways`() {
        val game = Klondike()
        val state = klondikeWith(
            tableau0 = listOf(card(Suit.SPADES, ACE)),
            tableau1 = listOf(card(Suit.HEARTS, 2)),
        )
        val move = game.tapTarget(state, Klondike.TABLEAU, 0)
        assertNotNull(move)
        assertEquals(PileKind.FOUNDATION, state[move!!.to].kind)
    }

    @Test
    fun `klondike tap will not shuffle a column between empty slots`() {
        val game = Klondike()
        val state = klondikeWith(tableau0 = listOf(card(Suit.SPADES, KING)))
        assertNull("a lone king has nowhere useful to go", game.tapTarget(state, Klondike.TABLEAU, 0))
    }

    @Test
    fun `klondike is won when all four foundations are complete`() {
        val game = Klondike()
        val piles = ArrayList<Pile>()
        piles += Pile(PileKind.STOCK)
        piles += Pile(PileKind.WASTE)
        for (suit in Suit.entries) {
            piles += Pile(PileKind.FOUNDATION, (ACE..KING).map { card(suit, it) })
        }
        repeat(7) { piles += Pile(PileKind.TABLEAU) }
        assertTrue(game.isWon(GameState(piles)))
    }

    private fun klondikeWith(
        tableau0: List<Card> = emptyList(),
        tableau1: List<Card> = emptyList(),
        tableau2: List<Card> = emptyList(),
        tableau3: List<Card> = emptyList(),
        tableau1FaceUpFrom: Int = 0,
    ): GameState {
        val piles = ArrayList<Pile>()
        piles += Pile(PileKind.STOCK)
        piles += Pile(PileKind.WASTE)
        repeat(4) { piles += Pile(PileKind.FOUNDATION) }
        piles += Pile(PileKind.TABLEAU, tableau0)
        piles += Pile(PileKind.TABLEAU, tableau1, faceUpFrom = tableau1FaceUpFrom)
        piles += Pile(PileKind.TABLEAU, tableau2)
        piles += Pile(PileKind.TABLEAU, tableau3)
        repeat(3) { piles += Pile(PileKind.TABLEAU) }
        return GameState(piles)
    }

    // ------------------------------------------------------------------ Spider

    @Test
    fun `spider deals fifty-four cards across ten columns`() {
        val game = Spider(4)
        val state = game.deal(Random(1))
        var dealt = 0
        for (column in 0 until 10) {
            val pile = state[Spider.TABLEAU + column]
            val expected = if (column < 4) 6 else 5
            assertEquals(expected, pile.size)
            assertEquals("only the last card shows", expected - 1, pile.faceUpFrom)
            dealt += pile.size
        }
        assertEquals(54, dealt)
        assertEquals(50, state[Spider.STOCK].size)
    }

    @Test
    fun `spider one-suit and two-suit decks repeat the right suits`() {
        assertEquals(1, spiderDeck(1).map { it.suit }.toSet().size)
        assertEquals(2, spiderDeck(2).map { it.suit }.toSet().size)
        assertEquals(4, spiderDeck(4).map { it.suit }.toSet().size)
        for (suits in listOf(1, 2, 4)) {
            val deck = spiderDeck(suits)
            assertEquals(104, deck.size)
            assertEquals("eight of every rank", 8, deck.count { it.rank == ACE })
        }
    }

    @Test
    fun `spider drops any suit but only lifts a suited run`() {
        val game = Spider(4)
        val state = spiderWith(
            listOf(card(Suit.SPADES, 8), card(Suit.HEARTS, 7)),
            listOf(card(Suit.DIAMONDS, 8)),
            listOf(card(Suit.SPADES, 9)),
        )
        val t = Spider.TABLEAU
        assertFalse("a mixed run cannot be lifted whole", game.canPickUp(state, t, 0))
        assertTrue("its top card can", game.canPickUp(state, t, 1))
        assertTrue("and lands on an eight of any suit", game.canDrop(state, t, 1, t + 1))
        assertFalse("but not on a nine", game.canDrop(state, t, 1, t + 2))
    }

    @Test
    fun `spider sweeps a finished suited run to a foundation`() {
        val game = Spider(4)
        val descending = (ACE..KING).reversed().map { card(Suit.SPADES, it) }
        val state = spiderWith(descending.dropLast(1), listOf(card(Suit.SPADES, ACE, uid = 99)))
        val t = Spider.TABLEAU
        val done = game.apply(state, Move.cards(t + 1, 0, t))!!
        assertEquals("the column is cleared", 0, done[t].size)
        assertEquals("the run is banked", 13, done[Spider.FOUNDATION].size)
        assertTrue("and pays a hundred", done.score > state.score)
    }

    @Test
    fun `spider refuses to deal onto an empty column`() {
        val game = Spider(4)
        var state = game.deal(Random(6))
        val t = Spider.TABLEAU
        state = state.with(t to Pile(PileKind.TABLEAU))
        assertNull(game.apply(state, Move.Deal))
    }

    @Test
    fun `spider deals one card to every column`() {
        val game = Spider(4)
        val state = game.deal(Random(6))
        val dealt = game.apply(state, Move.Deal)!!
        assertEquals(40, dealt[Spider.STOCK].size)
        for (column in 0 until 10) {
            val before = state[Spider.TABLEAU + column]
            val after = dealt[Spider.TABLEAU + column]
            assertEquals(before.size + 1, after.size)
            assertTrue("the new card is face up", after.isFaceUp(after.size - 1))
        }
    }

    private fun spiderWith(vararg columns: List<Card>): GameState {
        val piles = ArrayList<Pile>()
        piles += Pile(PileKind.STOCK)
        repeat(8) { piles += Pile(PileKind.FOUNDATION) }
        repeat(10) { piles += Pile(PileKind.TABLEAU, columns.getOrNull(it) ?: emptyList()) }
        return GameState(piles, score = 500)
    }

    // ---------------------------------------------------------------- FreeCell

    @Test
    fun `freecell deals eight face-up columns`() {
        val game = FreeCell()
        val state = game.deal(Random(1))
        var dealt = 0
        for (pile in state.piles.filter { it.kind == PileKind.TABLEAU }) {
            assertEquals("everything is face up", 0, pile.faceUpFrom)
            dealt += pile.size
        }
        assertEquals(52, dealt)
    }

    @Test
    fun `freecell run size follows the free cells and empty columns`() {
        val game = FreeCell()
        val state = game.deal(Random(1))
        val tableau = state.indicesOf(PileKind.TABLEAU)
        assertEquals("four cells, no empty columns", 5, game.maxRunSize(state, tableau[0]))

        val cleared = state.with(tableau[7] to Pile(PileKind.TABLEAU))
        assertEquals("one empty column doubles it", 10, game.maxRunSize(cleared, tableau[0]))
        assertEquals(
            "moving into that column does not count it",
            5,
            game.maxRunSize(cleared, tableau[7]),
        )

        val cells = state.indicesOf(PileKind.FREE_CELL)
        val filled = cleared.with(cells[0] to Pile(PileKind.FREE_CELL, listOf(card(Suit.SPADES, 4))))
        assertEquals("a used cell costs a card", 8, game.maxRunSize(filled, tableau[0]))
    }

    @Test
    fun `freecell refuses a run longer than the cells allow`() {
        val game = FreeCell()
        val run = listOf(
            card(Suit.SPADES, 6), card(Suit.HEARTS, 5), card(Suit.CLUBS, 4),
            card(Suit.DIAMONDS, 3), card(Suit.SPADES, 2),
        )
        val columns = mapOf(0 to listOf(card(Suit.HEARTS, 7)), 1 to run)
        val roomy = freeCellWith(columns, fillCells = 0)
        val tableau = roomy.indicesOf(PileKind.TABLEAU)
        assertTrue("the run itself is valid", game.canPickUp(roomy, tableau[1], 0))
        assertTrue("four free cells carry five cards", game.canDrop(roomy, tableau[1], 0, tableau[0]))

        val cramped = freeCellWith(columns, fillCells = 1)
        assertFalse("three free cells only carry four", game.canDrop(cramped, tableau[1], 0, tableau[0]))

        val withSpace = freeCellWith(columns, fillCells = 1, emptyColumns = setOf(7))
        assertTrue("an empty column doubles the reach", game.canDrop(withSpace, tableau[1], 0, tableau[0]))
    }

    @Test
    fun `freecell cells hold exactly one card`() {
        val game = FreeCell()
        val state = freeCellWith(mapOf(0 to listOf(card(Suit.SPADES, 9), card(Suit.HEARTS, 4))))
        val cells = state.indicesOf(PileKind.FREE_CELL)
        val tableau = state.indicesOf(PileKind.TABLEAU)
        assertTrue(game.canDrop(state, tableau[0], 1, cells[0]))
        val used = game.apply(state, Move.cards(tableau[0], 1, cells[0]))!!
        assertFalse("a full cell takes nothing more", game.canDrop(used, tableau[0], 0, cells[0]))
    }

    @Test
    fun `freecell has no stock`() {
        val game = FreeCell()
        val state = game.deal(Random(1))
        assertFalse(game.canDraw(state))
        assertNull(game.apply(state, Move.Draw))
    }

    /**
     * Columns not named in [columns] get a filler card, so that the run-size
     * rule is not quietly relaxed by a table full of empty columns.
     */
    private fun freeCellWith(
        columns: Map<Int, List<Card>>,
        fillCells: Int = 0,
        emptyColumns: Set<Int> = emptySet(),
    ): GameState {
        val piles = ArrayList<Pile>()
        repeat(4) {
            val held = if (it < fillCells) listOf(card(Suit.CLUBS, KING, uid = 200 + it)) else emptyList()
            piles += Pile(PileKind.FREE_CELL, held)
        }
        repeat(4) { piles += Pile(PileKind.FOUNDATION) }
        repeat(8) { column ->
            val cards = columns[column]
                ?: if (column in emptyColumns) emptyList()
                else listOf(card(Suit.DIAMONDS, KING, uid = 300 + column))
            piles += Pile(PileKind.TABLEAU, cards)
        }
        return GameState(piles)
    }

    // ---------------------------------------------------------------- TriPeaks

    @Test
    fun `tripeaks deals three peaks and a turned card`() {
        val game = TriPeaks()
        val state = game.deal(Random(1))
        assertEquals(28, (0 until 28).count { state[TriPeaks.TABLEAU + it].isNotEmpty })
        assertEquals(23, state[TriPeaks.STOCK].size)
        assertEquals(1, state[TriPeaks.WASTE].size)
    }

    @Test
    fun `tripeaks shows the base row and hides the rest`() {
        val game = TriPeaks()
        val state = game.deal(Random(1))
        for (slot in 0 until 18) {
            assertFalse("slot $slot is covered", game.isOpen(state, slot))
            assertFalse(state[TriPeaks.TABLEAU + slot].isFaceUp(0))
        }
        for (slot in 18 until 28) {
            assertTrue("base slot $slot is open", game.isOpen(state, slot))
            assertTrue(state[TriPeaks.TABLEAU + slot].isFaceUp(0))
        }
    }

    @Test
    fun `tripeaks takes a card one rank away and wraps at the ace`() {
        val game = TriPeaks()
        val state = triPeaksWith(
            base = mapOf(18 to card(Suit.SPADES, 5), 19 to card(Suit.HEARTS, 7), 20 to card(Suit.CLUBS, KING)),
            waste = card(Suit.DIAMONDS, 6),
        )
        val t = TriPeaks.TABLEAU
        assertTrue("five is next to six", game.canDrop(state, t + 18, 0, TriPeaks.WASTE))
        assertTrue("so is seven", game.canDrop(state, t + 19, 0, TriPeaks.WASTE))
        assertFalse("a king is not", game.canDrop(state, t + 20, 0, TriPeaks.WASTE))

        val onAce = triPeaksWith(
            base = mapOf(18 to card(Suit.CLUBS, KING), 19 to card(Suit.SPADES, 2)),
            waste = card(Suit.DIAMONDS, ACE),
        )
        assertTrue("king wraps onto ace", game.canDrop(onAce, t + 18, 0, TriPeaks.WASTE))
        assertTrue("and so does the two", game.canDrop(onAce, t + 19, 0, TriPeaks.WASTE))
    }

    @Test
    fun `tripeaks without wrapping keeps king and ace apart`() {
        val game = TriPeaks(wrapAces = false)
        val state = triPeaksWith(
            base = mapOf(18 to card(Suit.CLUBS, KING)),
            waste = card(Suit.DIAMONDS, ACE),
        )
        assertFalse(game.canDrop(state, TriPeaks.TABLEAU + 18, 0, TriPeaks.WASTE))
    }

    @Test
    fun `tripeaks uncovers the card above once both its children are gone`() {
        val game = TriPeaks()
        val state = triPeaksWith(
            base = mapOf(18 to card(Suit.SPADES, 5), 19 to card(Suit.SPADES, 4)),
            covered = mapOf(9 to card(Suit.HEARTS, 4)),
            waste = card(Suit.DIAMONDS, 6),
        )
        val t = TriPeaks.TABLEAU
        assertFalse(game.isOpen(state, 9))
        val once = game.apply(state, Move.cards(t + 18, 0, TriPeaks.WASTE))!!
        assertFalse("one child left", once.let { game.isOpen(it, 9) })
        val twice = game.apply(once, Move.cards(t + 19, 0, TriPeaks.WASTE))!!
        assertTrue("now it is open", game.isOpen(twice, 9))
        assertTrue("and face up", twice[t + 9].isFaceUp(0))
    }

    @Test
    fun `tripeaks pays a growing streak and a draw resets it`() {
        val game = TriPeaks()
        val state = triPeaksWith(
            base = mapOf(18 to card(Suit.SPADES, 5), 19 to card(Suit.HEARTS, 4)),
            waste = card(Suit.DIAMONDS, 6),
            stock = listOf(card(Suit.CLUBS, 9)),
        )
        val t = TriPeaks.TABLEAU
        val one = game.apply(state, Move.cards(t + 18, 0, TriPeaks.WASTE))!!
        val two = game.apply(one, Move.cards(t + 19, 0, TriPeaks.WASTE))!!
        assertEquals(1, one.streak)
        assertEquals(2, two.streak)
        assertTrue("the second card is worth more", two.score - one.score > one.score - state.score)
        val drawn = game.apply(two, Move.Draw)!!
        assertEquals("drawing breaks the chain", 0, drawn.streak)
    }

    private fun triPeaksWith(
        base: Map<Int, Card>,
        covered: Map<Int, Card> = emptyMap(),
        waste: Card,
        stock: List<Card> = emptyList(),
    ): GameState {
        val piles = ArrayList<Pile>()
        piles += Pile(PileKind.STOCK, stock, faceUpFrom = stock.size)
        piles += Pile(PileKind.WASTE, listOf(waste))
        repeat(28) { slot ->
            val card = base[slot] ?: covered[slot]
            piles += Pile(PileKind.RESERVE, listOfNotNull(card))
        }
        return GameState(piles)
    }

    // ----------------------------------------------------------------- Pyramid

    @Test
    fun `pyramid deals twenty-eight cards in seven rows`() {
        val game = Pyramid()
        val state = game.deal(Random(1))
        assertEquals(28, (0 until Pyramid.SLOTS).count { state[Pyramid.PYRAMID + it].isNotEmpty })
        assertEquals(24, state[Pyramid.STOCK].size)
        for (slot in 0 until 21) assertFalse("slot $slot is covered", game.isOpen(state, slot))
        for (slot in 21 until 28) assertTrue("row seven is open", game.isOpen(state, slot))
    }

    @Test
    fun `pyramid pairs cards that add to thirteen`() {
        val game = Pyramid()
        val state = pyramidWith(mapOf(21 to card(Suit.SPADES, 8), 22 to card(Suit.HEARTS, 5), 23 to card(Suit.CLUBS, 6)))
        val p = Pyramid.PYRAMID
        assertTrue("eight and five", game.canDrop(state, p + 21, 0, p + 22))
        assertFalse("eight and six", game.canDrop(state, p + 21, 0, p + 23))
        val cleared = game.apply(state, Move.cards(p + 21, 0, p + 22))!!
        assertTrue(cleared[p + 21].isEmpty)
        assertTrue(cleared[p + 22].isEmpty)
        assertEquals("both go to the discard", 2, cleared[Pyramid.DISCARD].size)
    }

    @Test
    fun `pyramid takes a king on its own`() {
        val game = Pyramid()
        val state = pyramidWith(mapOf(21 to card(Suit.SPADES, KING)))
        val p = Pyramid.PYRAMID
        val tap = game.tapTarget(state, p + 21, 0)
        assertEquals(Move.cards(p + 21, 0, Pyramid.DISCARD), tap)
        val cleared = game.apply(state, tap!!)!!
        assertTrue(cleared[p + 21].isEmpty)
        assertEquals(1, cleared[Pyramid.DISCARD].size)
    }

    @Test
    fun `pyramid will not touch a covered card`() {
        val game = Pyramid()
        val state = pyramidWith(
            mapOf(15 to card(Suit.SPADES, 8), 21 to card(Suit.HEARTS, 5), 22 to card(Suit.CLUBS, 9)),
        )
        val p = Pyramid.PYRAMID
        assertFalse("row six is still covered", game.isOpen(state, 15))
        assertFalse(game.canDrop(state, p + 15, 0, p + 21))
    }

    @Test
    fun `pyramid pairs the waste with the figure`() {
        val game = Pyramid()
        val state = pyramidWith(
            slots = mapOf(21 to card(Suit.SPADES, 9)),
            waste = listOf(card(Suit.HEARTS, 4)),
        )
        val p = Pyramid.PYRAMID
        assertTrue(game.canDrop(state, Pyramid.WASTE, 0, p + 21))
        val cleared = game.apply(state, Move.cards(Pyramid.WASTE, 0, p + 21))!!
        assertTrue(cleared[Pyramid.WASTE].isEmpty)
        assertTrue(cleared[p + 21].isEmpty)
    }

    @Test
    fun `pyramid limits how often the waste goes back`() {
        val game = Pyramid(redeals = 1)
        var state = game.deal(Random(3))
        repeat(24) { state = game.apply(state, Move.Draw)!! }
        state = game.apply(state, Move.Recycle)!!
        assertEquals(24, state[Pyramid.STOCK].size)
        repeat(24) { state = game.apply(state, Move.Draw)!! }
        assertNull("only one redeal was allowed", game.apply(state, Move.Recycle))
        assertFalse(game.canDraw(state))
    }

    private fun pyramidWith(slots: Map<Int, Card>, waste: List<Card> = emptyList()): GameState {
        val piles = ArrayList<Pile>()
        piles += Pile(PileKind.STOCK)
        piles += Pile(PileKind.WASTE, waste)
        piles += Pile(PileKind.FOUNDATION)
        repeat(Pyramid.SLOTS) { piles += Pile(PileKind.RESERVE, listOfNotNull(slots[it])) }
        return GameState(piles)
    }
}
