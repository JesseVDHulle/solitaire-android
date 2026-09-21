package com.jvdh.solitaire.engine.games

import com.jvdh.solitaire.engine.Card
import com.jvdh.solitaire.engine.Fan
import com.jvdh.solitaire.engine.GameId
import com.jvdh.solitaire.engine.GameState
import com.jvdh.solitaire.engine.KING
import com.jvdh.solitaire.engine.Move
import com.jvdh.solitaire.engine.MoveKind
import com.jvdh.solitaire.engine.Pile
import com.jvdh.solitaire.engine.PileKind
import com.jvdh.solitaire.engine.Slot
import com.jvdh.solitaire.engine.SolitaireGame
import com.jvdh.solitaire.engine.TableLayout
import com.jvdh.solitaire.engine.standardDeck
import kotlin.random.Random

/**
 * Pyramid: clear 28 cards by pairing them to thirteen. Aces count one, Jacks
 * eleven, Queens twelve, and a King is worth thirteen on its own.
 *
 * Both halves of a pair must be uncovered; the waste top counts as uncovered,
 * so a card can be paired with the card just turned.
 *
 * @param redeals how many times the waste may be turned back into the stock.
 */
class Pyramid(private val redeals: Int = 2) : SolitaireGame {

    override val id = GameId.PYRAMID

    override val variantLabel: String =
        if (redeals == 0) "One pass" else "${redeals + 1} passes"

    private val rows = 7

    /** For each pyramid slot, the two slots resting on it. */
    private val covers: List<List<Int>> = buildCovers()

    private fun buildCovers(): List<List<Int>> {
        val rowStart = IntArray(rows)
        for (row in 1 until rows) rowStart[row] = rowStart[row - 1] + row
        val out = MutableList(SLOTS) { emptyList<Int>() }
        for (row in 0 until rows - 1) {
            for (i in 0..row) {
                val below = rowStart[row + 1] + i
                out[rowStart[row] + i] = listOf(below, below + 1)
            }
        }
        return out
    }

    override val layout: TableLayout = run {
        val step = 1.04f
        val rowDrop = 0.44f
        val slots = ArrayList<Slot>(SLOTS + 3)
        var slot = 0
        for (row in 0 until rows) {
            val left = (rows - 1 - row) * 0.5f
            for (i in 0..row) {
                slots += Slot(PYRAMID + slot, (left + i) * step, row * rowDrop)
                slot++
            }
        }
        val bottom = (rows - 1) * rowDrop + 1.15f
        slots += Slot(STOCK, 1.4f * step, bottom)
        slots += Slot(WASTE, 2.6f * step, bottom, Fan.RIGHT, fanLimit = 2)
        slots += Slot(DISCARD, 4.6f * step, bottom)
        TableLayout(slots, width = 6 * step + 1f, height = bottom + 1.05f)
    }

    override fun deal(rng: Random): GameState {
        val deck = standardDeck().shuffled(rng).toMutableList()
        val piles = ArrayList<Pile>(SLOTS + 3)
        piles += Pile(PileKind.STOCK)
        piles += Pile(PileKind.WASTE)
        piles += Pile(PileKind.FOUNDATION)
        repeat(SLOTS) {
            val card = deck.removeAt(deck.size - 1)
            piles += Pile(PileKind.RESERVE, listOf(card), faceUpFrom = 0)
        }
        val state = GameState(piles)
        return state.with(STOCK to Pile(PileKind.STOCK, deck.toList(), faceUpFrom = deck.size))
    }

    /** True when nothing rests on that pyramid slot. */
    fun isOpen(state: GameState, slot: Int): Boolean =
        state[PYRAMID + slot].isNotEmpty && covers[slot].all { state[PYRAMID + it].isEmpty }

    override fun isWon(state: GameState): Boolean =
        (0 until SLOTS).all { state[PYRAMID + it].isEmpty }

    override fun canPickUp(state: GameState, pile: Int, index: Int): Boolean {
        val source = state[pile]
        if (index != source.size - 1 || source.isEmpty) return false
        return when (source.kind) {
            PileKind.RESERVE -> isOpen(state, pile - PYRAMID)
            PileKind.WASTE -> true
            else -> false
        }
    }

    override fun canDrop(state: GameState, pile: Int, index: Int, to: Int): Boolean {
        if (!canPickUp(state, pile, index)) return false
        val card = state[pile].cards[index]
        if (to == DISCARD) return card.rank == KING
        if (to == pile) return false
        if (!canPickUp(state, to, state[to].size - 1)) return false
        val other = state[to].top ?: return false
        return card.rank + other.rank == KING
    }

    override fun apply(state: GameState, move: Move): GameState? = when (move.kind) {
        MoveKind.CARDS -> applyMatch(state, move)
        MoveKind.DRAW -> applyDraw(state)
        MoveKind.RECYCLE -> applyRecycle(state)
        else -> null
    }

    /**
     * A "move" in Pyramid removes a pair: the dragged card and whatever it was
     * dropped on both go to the discard. A King is dropped on the discard alone.
     */
    private fun applyMatch(state: GameState, move: Move): GameState? {
        if (!canDrop(state, move.from, move.index, move.to)) return null
        val from = state[move.from]
        val card = from.cards[move.index]
        val removed = ArrayList<Card>(2)
        removed += card
        var next = state.with(move.from to from.take(move.index))
        if (move.to != DISCARD) {
            val other = next[move.to]
            removed += other.top!!
            next = next.with(move.to to other.take(other.size - 1))
        }
        return next
            .with(DISCARD to next[DISCARD].plus(removed))
            .copy(
                moves = state.moves + 1,
                score = state.score + removed.size * 5 +
                    (clearedRows(next) - clearedRows(state)) * 50,
            )
    }

    /** How many pyramid rows are completely gone. */
    private fun clearedRows(state: GameState): Int {
        val rowStart = IntArray(rows)
        for (row in 1 until rows) rowStart[row] = rowStart[row - 1] + row
        return (0 until rows).count { row ->
            (0..row).all { state[PYRAMID + rowStart[row] + it].isEmpty }
        }
    }

    private fun applyDraw(state: GameState): GameState? {
        val stock = state[STOCK]
        if (stock.isEmpty) return null
        val card = stock.top!!
        return state
            .with(
                STOCK to stock.take(stock.size - 1),
                WASTE to state[WASTE].plus(listOf(card)),
            )
            .copy(moves = state.moves + 1)
    }

    private fun applyRecycle(state: GameState): GameState? {
        val stock = state[STOCK]
        val waste = state[WASTE]
        if (stock.isNotEmpty || waste.isEmpty) return null
        if (state.passes >= redeals) return null
        val recycled = waste.cards.reversed()
        return state
            .with(
                STOCK to Pile(PileKind.STOCK, recycled, faceUpFrom = recycled.size),
                WASTE to Pile(PileKind.WASTE),
            )
            .copy(passes = state.passes + 1, moves = state.moves + 1)
    }

    override fun canDraw(state: GameState): Boolean =
        state[STOCK].isNotEmpty || (state[WASTE].isNotEmpty && state.passes < redeals)

    override fun tapTarget(state: GameState, pile: Int, index: Int): Move? {
        if (state[pile].kind == PileKind.STOCK) {
            return if (state[pile].isNotEmpty) Move.Draw else if (canDraw(state)) Move.Recycle else null
        }
        if (!canPickUp(state, pile, index)) return null
        val card = state[pile].cards[index]
        // Kings never need a partner, so a tap plays them straight away.
        if (card.rank == KING) return Move.cards(pile, index, DISCARD)
        // Anything else waits for the player to pick a partner.
        return null
    }

    override fun autoFinishMove(state: GameState): Move? = null

    override fun hint(state: GameState): Move? {
        val open = (0 until SLOTS).map { PYRAMID + it }.filter { isOpen(state, it - PYRAMID) } +
            listOfNotNull(WASTE.takeIf { state[WASTE].isNotEmpty })
        for (pile in open) {
            val index = state[pile].size - 1
            if (state[pile].cards[index].rank == KING) return Move.cards(pile, index, DISCARD)
        }
        for (a in open) {
            for (b in open) {
                if (a == b) continue
                if (canDrop(state, a, state[a].size - 1, b)) return Move.cards(a, state[a].size - 1, b)
            }
        }
        if (state[STOCK].isNotEmpty) return Move.Draw
        if (canDraw(state)) return Move.Recycle
        return null
    }

    override fun statusText(state: GameState): String {
        val left = (0 until SLOTS).count { state[PYRAMID + it].isNotEmpty }
        val passesLeft = redeals - state.passes
        return "Pyramid $left/28 · Stock ${state[STOCK].size} · Redeals $passesLeft"
    }

    companion object {
        const val STOCK = 0
        const val WASTE = 1
        const val DISCARD = 2
        const val PYRAMID = 3
        const val SLOTS = 28
    }
}
