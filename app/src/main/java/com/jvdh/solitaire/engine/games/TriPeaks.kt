package com.jvdh.solitaire.engine.games

import com.jvdh.solitaire.engine.Fan
import com.jvdh.solitaire.engine.GameId
import com.jvdh.solitaire.engine.GameState
import com.jvdh.solitaire.engine.Move
import com.jvdh.solitaire.engine.MoveKind
import com.jvdh.solitaire.engine.Pile
import com.jvdh.solitaire.engine.PileKind
import com.jvdh.solitaire.engine.Slot
import com.jvdh.solitaire.engine.SolitaireGame
import com.jvdh.solitaire.engine.TableLayout
import com.jvdh.solitaire.engine.ranksWrapAdjacent
import com.jvdh.solitaire.engine.standardDeck
import kotlin.random.Random

/**
 * TriPeaks: three overlapping peaks of 28 cards. Any uncovered card one rank
 * above or below the top of the waste can be taken, and Ace wraps round to King
 * in both directions. Long chains are where the points are.
 *
 * @param wrapAces when false, Ace only joins to 2, which is the harder variant.
 */
class TriPeaks(private val wrapAces: Boolean = true) : SolitaireGame {

    override val id = GameId.TRIPEAKS

    override val variantLabel: String =
        if (wrapAces) "Aces wrap · classic" else "No wrap · hard"

    /** Row sizes of the figure, from the three peaks down to the open base. */
    private val rowSizes = intArrayOf(3, 6, 9, 10)

    /** For each slot, the slots directly on top of it. */
    private val covers: List<List<Int>> = buildCovers()

    private fun buildCovers(): List<List<Int>> {
        val rowStart = IntArray(rowSizes.size)
        for (row in 1 until rowSizes.size) rowStart[row] = rowStart[row - 1] + rowSizes[row - 1]
        val out = MutableList(28) { emptyList<Int>() }
        for (peak in 0 until 3) {
            out[rowStart[0] + peak] = listOf(rowStart[1] + peak * 2, rowStart[1] + peak * 2 + 1)
        }
        for (i in 0 until 6) {
            val peak = i / 2
            val offset = i % 2
            val left = rowStart[2] + peak * 3 + offset
            out[rowStart[1] + i] = listOf(left, left + 1)
        }
        for (i in 0 until 9) {
            out[rowStart[2] + i] = listOf(rowStart[3] + i, rowStart[3] + i + 1)
        }
        return out
    }

    override val layout: TableLayout = run {
        val step = 1.02f
        val rowDrop = 0.36f
        val slots = ArrayList<Slot>(30)
        // x positions in card widths; each row is offset half a card from the next.
        val xs = listOf(
            listOf(1.5f, 4.5f, 7.5f),
            listOf(1f, 2f, 4f, 5f, 7f, 8f),
            listOf(0.5f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6.5f, 7.5f, 8.5f),
            listOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f),
        )
        var slot = 0
        for (row in rowSizes.indices) {
            for (x in xs[row]) {
                slots += Slot(TABLEAU + slot, x * step, row * rowDrop)
                slot++
            }
        }
        val bottom = 3 * rowDrop + 1.15f
        slots += Slot(STOCK, 3f * step, bottom)
        slots += Slot(WASTE, 4.4f * step, bottom, Fan.RIGHT, fanLimit = 2)
        TableLayout(slots, width = 9 * step + 1f, height = bottom + 1.05f)
    }

    override fun deal(rng: Random): GameState {
        val deck = standardDeck().shuffled(rng).toMutableList()
        val piles = ArrayList<Pile>(30)
        piles += Pile(PileKind.STOCK)
        piles += Pile(PileKind.WASTE)
        repeat(28) {
            val card = deck.removeAt(deck.size - 1)
            piles += Pile(PileKind.RESERVE, listOf(card), faceUpFrom = 0)
        }
        val turned = deck.removeAt(deck.size - 1)
        var state = GameState(piles)
        state = state.with(
            STOCK to Pile(PileKind.STOCK, deck.toList(), faceUpFrom = deck.size),
            WASTE to Pile(PileKind.WASTE, listOf(turned), faceUpFrom = 0),
        )
        return refreshFaces(state)
    }

    /** A figure card shows its face once nothing rests on it. */
    private fun refreshFaces(state: GameState): GameState {
        var next = state
        for (i in 0 until 28) {
            val pile = next[TABLEAU + i]
            if (pile.isEmpty) continue
            val open = covers[i].all { next[TABLEAU + it].isEmpty }
            val faceUpFrom = if (open) 0 else 1
            if (pile.faceUpFrom != faceUpFrom) {
                next = next.with(TABLEAU + i to pile.copy(faceUpFrom = faceUpFrom))
            }
        }
        return next
    }

    fun isOpen(state: GameState, slot: Int): Boolean =
        state[TABLEAU + slot].isNotEmpty && covers[slot].all { state[TABLEAU + it].isEmpty }

    override fun isWon(state: GameState): Boolean =
        (0 until 28).all { state[TABLEAU + it].isEmpty }

    override fun canPickUp(state: GameState, pile: Int, index: Int): Boolean {
        if (pile < TABLEAU || index != 0) return false
        return isOpen(state, pile - TABLEAU)
    }

    override fun canDrop(state: GameState, pile: Int, index: Int, to: Int): Boolean {
        if (to != WASTE || !canPickUp(state, pile, index)) return false
        val card = state[pile].cards[index]
        val top = state[WASTE].top ?: return false
        return if (wrapAces) ranksWrapAdjacent(card.rank, top.rank)
        else kotlin.math.abs(card.rank - top.rank) == 1
    }

    override fun apply(state: GameState, move: Move): GameState? = when (move.kind) {
        MoveKind.CARDS -> applyCards(state, move)
        MoveKind.DRAW -> applyDraw(state)
        else -> null
    }

    private fun applyCards(state: GameState, move: Move): GameState? {
        if (!canDrop(state, move.from, move.index, move.to)) return null
        val card = state[move.from].cards[move.index]
        val slot = move.from - TABLEAU
        val streak = state.streak + 1
        // Classic chain scoring: the nth card of a run is worth n, and each peak
        // uncovered pays a bonus.
        val peakBonus = if (slot < 3) 500 else 0
        var next = state
            .with(
                move.from to Pile(PileKind.RESERVE),
                WASTE to state[WASTE].plus(listOf(card)),
            )
            .copy(
                moves = state.moves + 1,
                streak = streak,
                score = state.score + streak * 10 + peakBonus,
            )
        next = refreshFaces(next)
        if (isWon(next)) next = next.copy(score = next.score + 1000 + next[STOCK].size * 50)
        return next
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
            .copy(moves = state.moves + 1, streak = 0)
    }

    override fun canDraw(state: GameState): Boolean = state[STOCK].isNotEmpty

    override fun tapTarget(state: GameState, pile: Int, index: Int): Move? {
        if (state[pile].kind == PileKind.STOCK) return Move.Draw
        if (!canDrop(state, pile, index, WASTE)) return null
        return Move.cards(pile, index, WASTE)
    }

    override fun autoFinishMove(state: GameState): Move? = null

    override fun hint(state: GameState): Move? {
        // Prefer the card that frees the most, which in practice means the
        // highest row still in play.
        var best: Move? = null
        var bestRow = Int.MAX_VALUE
        for (i in 0 until 28) {
            val pile = TABLEAU + i
            if (!canDrop(state, pile, 0, WASTE)) continue
            val row = rowOf(i)
            if (row < bestRow) {
                bestRow = row
                best = Move.cards(pile, 0, WASTE)
            }
        }
        best?.let { return it }
        return if (state[STOCK].isNotEmpty) Move.Draw else null
    }

    private fun rowOf(slot: Int): Int {
        var remaining = slot
        for (row in rowSizes.indices) {
            if (remaining < rowSizes[row]) return row
            remaining -= rowSizes[row]
        }
        return rowSizes.size - 1
    }

    override fun statusText(state: GameState): String {
        val left = (0 until 28).count { state[TABLEAU + it].isNotEmpty }
        return "Cards left $left · Stock ${state[STOCK].size} · Streak ${state.streak}"
    }

    companion object {
        const val STOCK = 0
        const val WASTE = 1
        const val TABLEAU = 2
    }
}
