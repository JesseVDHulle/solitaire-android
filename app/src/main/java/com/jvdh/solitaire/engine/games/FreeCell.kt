package com.jvdh.solitaire.engine.games

import com.jvdh.solitaire.engine.Card
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
import com.jvdh.solitaire.engine.acceptsOnAlternatingTableau
import com.jvdh.solitaire.engine.acceptsOnFoundation
import com.jvdh.solitaire.engine.isAlternatingRun
import com.jvdh.solitaire.engine.isSafeToAutoPlay
import com.jvdh.solitaire.engine.standardDeck
import kotlin.random.Random

/**
 * FreeCell: everything face up, four holding cells, no stock.
 *
 * Moving a run of several cards is really a series of single-card moves through
 * the cells, so the size of a legal run is `(free cells + 1) * 2 ^ (empty
 * columns)` -- halved when the destination is itself one of those empty columns.
 *
 * @param cells how many free cells; four is standard, fewer is harder.
 */
class FreeCell(private val cells: Int = 4) : SolitaireGame {

    override val id = GameId.FREECELL

    override val variantLabel: String = "$cells free cells"

    /** Pile indices. The cell count is configurable, so the bases shift with it. */
    private val free = 0
    private val foundation = cells
    private val tableau = cells + 4

    override val layout: TableLayout = run {
        val step = 1.08f
        val slots = ArrayList<Slot>(16)
        for (i in 0 until cells) slots += Slot(free + i, i * step, 0f)
        for (i in 0 until 4) slots += Slot(foundation + i, (cells + i) * step, 0f)
        for (i in 0 until 8) slots += Slot(tableau + i, i * step, 1.22f, Fan.DOWN)
        val columns = maxOf(8, cells + 4)
        TableLayout(slots, width = (columns - 1) * step + 1f, height = 4.7f)
    }

    override fun deal(rng: Random): GameState {
        val deck = standardDeck().shuffled(rng).toMutableList()
        val piles = ArrayList<Pile>(16)
        repeat(cells) { piles += Pile(PileKind.FREE_CELL) }
        repeat(4) { piles += Pile(PileKind.FOUNDATION) }
        for (column in 0 until 8) {
            val count = if (column < 4) 7 else 6
            val cards = ArrayList<Card>(count)
            repeat(count) { cards += deck.removeAt(deck.size - 1) }
            piles += Pile(PileKind.TABLEAU, cards, faceUpFrom = 0)
        }
        return GameState(piles)
    }

    override fun isWon(state: GameState): Boolean =
        (0 until 4).all { state[foundation + it].size == 13 }

    override fun canPickUp(state: GameState, pile: Int, index: Int): Boolean {
        val source = state[pile]
        if (index !in source.cards.indices) return false
        return when (source.kind) {
            PileKind.TABLEAU -> isAlternatingRun(source.cards, index)
            PileKind.FREE_CELL, PileKind.FOUNDATION -> index == source.size - 1
            else -> false
        }
    }

    override fun canDrop(state: GameState, pile: Int, index: Int, to: Int): Boolean {
        if (to == pile) return false
        if (!canPickUp(state, pile, index)) return false
        val source = state[pile]
        val card = source.cards[index]
        val count = source.size - index
        val target = state[to]
        return when (target.kind) {
            PileKind.FREE_CELL -> count == 1 && target.isEmpty
            PileKind.FOUNDATION -> count == 1 && acceptsOnFoundation(target, card)
            PileKind.TABLEAU ->
                acceptsOnAlternatingTableau(target, card) && count <= maxRunSize(state, to)
            else -> false
        }
    }

    /** How many cards can be relocated in one gesture right now. */
    fun maxRunSize(state: GameState, destination: Int): Int {
        val freeCells = (0 until cells).count { state[free + it].isEmpty }
        var emptyColumns = (0 until 8).count { state[tableau + it].isEmpty }
        if (state[destination].kind == PileKind.TABLEAU && state[destination].isEmpty) {
            emptyColumns -= 1
        }
        var capacity = freeCells + 1
        repeat(emptyColumns.coerceAtLeast(0)) { capacity *= 2 }
        return capacity
    }

    override fun apply(state: GameState, move: Move): GameState? {
        if (move.kind != MoveKind.CARDS) return null
        if (!canDrop(state, move.from, move.index, move.to)) return null
        val source = state[move.from]
        val moving = source.cards.subList(move.index, source.size).toList()
        return state
            .with(move.from to source.take(move.index), move.to to state[move.to].plus(moving))
            .copy(moves = state.moves + 1)
    }

    override fun canDraw(state: GameState): Boolean = false

    override fun tapTarget(state: GameState, pile: Int, index: Int): Move? {
        if (!canPickUp(state, pile, index)) return null
        val source = state[pile]
        val single = index == source.size - 1
        if (single) {
            for (i in 0 until 4) {
                val home = foundation + i
                if (canDrop(state, pile, index, home)) return Move.cards(pile, index, home)
            }
        }
        for (i in 0 until 8) {
            val column = tableau + i
            if (state[column].isEmpty) continue
            if (canDrop(state, pile, index, column)) return Move.cards(pile, index, column)
        }
        if (single && source.kind == PileKind.TABLEAU) {
            for (i in 0 until cells) {
                val cell = free + i
                if (canDrop(state, pile, index, cell)) return Move.cards(pile, index, cell)
            }
        }
        if (index > 0 || source.kind != PileKind.TABLEAU) {
            for (i in 0 until 8) {
                val column = tableau + i
                if (state[column].isNotEmpty) continue
                if (canDrop(state, pile, index, column)) return Move.cards(pile, index, column)
            }
        }
        return null
    }

    override fun autoFinishMove(state: GameState): Move? {
        val sources = (0 until cells).map { free + it } + (0 until 8).map { tableau + it }
        for (source in sources) {
            val top = state[source].size - 1
            if (top < 0) continue
            for (i in 0 until 4) {
                val home = foundation + i
                if (canDrop(state, source, top, home)) return Move.cards(source, top, home)
            }
        }
        return null
    }

    /** The subset of [autoFinishMove] that can never strand a lower card. */
    fun safeAutoPlay(state: GameState): Move? {
        val move = autoFinishMove(state) ?: return null
        val card = state[move.from].cards[move.index]
        val foundations = (0 until 4).map { state[foundation + it] }
        return if (isSafeToAutoPlay(foundations, card)) move else null
    }

    override fun hint(state: GameState): Move? {
        autoFinishMove(state)?.let { return it }
        var fallback: Move? = null
        for (i in 0 until 8) {
            val from = tableau + i
            val source = state[from]
            for (index in source.cards.indices) {
                if (!canPickUp(state, from, index)) continue
                for (j in 0 until 8) {
                    val to = tableau + j
                    if (to == from || state[to].isEmpty) continue
                    if (!canDrop(state, from, index, to)) continue
                    // Emptying a column is the strongest move in FreeCell.
                    if (index == 0) return Move.cards(from, index, to)
                    if (fallback == null) fallback = Move.cards(from, index, to)
                }
            }
        }
        for (i in 0 until cells) {
            val cell = free + i
            if (state[cell].isEmpty) continue
            for (j in 0 until 8) {
                val to = tableau + j
                if (canDrop(state, cell, 0, to)) return Move.cards(cell, 0, to)
            }
        }
        return fallback
    }

    override fun statusText(state: GameState): String {
        val openCells = (0 until cells).count { state[free + it].isEmpty }
        val built = (0 until 4).sumOf { state[foundation + it].size }
        return "Cells $openCells/$cells · Home $built/52 · Moves ${state.moves}"
    }
}
