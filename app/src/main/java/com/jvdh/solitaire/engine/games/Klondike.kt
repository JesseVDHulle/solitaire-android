package com.jvdh.solitaire.engine.games

import com.jvdh.solitaire.engine.ACE
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
import com.jvdh.solitaire.engine.acceptsOnAlternatingTableau
import com.jvdh.solitaire.engine.acceptsOnFoundation
import com.jvdh.solitaire.engine.isAlternatingRun
import com.jvdh.solitaire.engine.isSafeToAutoPlay
import com.jvdh.solitaire.engine.standardDeck
import kotlin.random.Random

enum class ScoreMode { NONE, STANDARD, VEGAS }

/**
 * Klondike: seven columns, four foundations, and a stock the player turns over
 * one or three cards at a time.
 *
 * @param drawCount cards turned per draw, 1 or 3.
 * @param redeals how many times the waste may go back under the stock; -1 for
 *   unlimited.
 */
class Klondike(
    private val drawCount: Int = 1,
    private val redeals: Int = -1,
    private val scoreMode: ScoreMode = ScoreMode.STANDARD,
) : SolitaireGame {

    override val id = GameId.KLONDIKE

    override val variantLabel: String = buildString {
        append("Draw ").append(drawCount)
        append(" · ")
        append(if (redeals < 0) "unlimited redeals" else if (redeals == 0) "no redeal" else "$redeals redeals")
    }

    override val layout: TableLayout = run {
        val step = 1.09f
        val slots = ArrayList<Slot>(13)
        slots += Slot(STOCK, 0f, 0f)
        slots += Slot(WASTE, step, 0f, Fan.RIGHT, fanLimit = 3)
        for (i in 0 until 4) slots += Slot(FOUNDATION + i, (3 + i) * step, 0f)
        for (i in 0 until 7) slots += Slot(TABLEAU + i, i * step, 1.22f, Fan.DOWN)
        TableLayout(slots, width = 6 * step + 1f, height = 4.6f)
    }

    override fun deal(rng: Random): GameState {
        val deck = standardDeck().shuffled(rng).toMutableList()
        val piles = ArrayList<Pile>(13)
        val tableau = ArrayList<Pile>(7)
        for (column in 0 until 7) {
            val cards = ArrayList<Card>(column + 1)
            repeat(column + 1) { cards += deck.removeAt(deck.size - 1) }
            tableau += Pile(PileKind.TABLEAU, cards, faceUpFrom = column)
        }
        piles += Pile(PileKind.STOCK, deck.toList(), faceUpFrom = deck.size)
        piles += Pile(PileKind.WASTE)
        repeat(4) { piles += Pile(PileKind.FOUNDATION) }
        piles += tableau
        val start = if (scoreMode == ScoreMode.VEGAS) -52 else 0
        return GameState(piles, score = start)
    }

    override fun isWon(state: GameState): Boolean =
        (0 until 4).all { state[FOUNDATION + it].size == 13 }

    override fun canPickUp(state: GameState, pile: Int, index: Int): Boolean {
        val source = state[pile]
        if (index !in source.cards.indices || !source.isFaceUp(index)) return false
        return when (source.kind) {
            PileKind.TABLEAU -> isAlternatingRun(source.cards, index)
            PileKind.WASTE, PileKind.FOUNDATION -> index == source.size - 1
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
            PileKind.FOUNDATION -> count == 1 && acceptsOnFoundation(target, card)
            PileKind.TABLEAU -> acceptsOnAlternatingTableau(target, card)
            else -> false
        }
    }

    override fun apply(state: GameState, move: Move): GameState? = when (move.kind) {
        MoveKind.CARDS -> applyCards(state, move)
        MoveKind.DRAW -> applyDraw(state)
        MoveKind.RECYCLE -> applyRecycle(state)
        MoveKind.DEAL -> null
    }

    private fun applyCards(state: GameState, move: Move): GameState? {
        if (!canDrop(state, move.from, move.index, move.to)) return null
        val source = state[move.from]
        val target = state[move.to]
        val moving = source.cards.subList(move.index, source.size).toList()
        val trimmed = source.take(move.index)
        val revealed = trimmed.revealTop()
        val flipped = source.kind == PileKind.TABLEAU && revealed.faceUpFrom != trimmed.faceUpFrom
        return state
            .with(move.from to revealed, move.to to target.plus(moving))
            .copy(
                moves = state.moves + 1,
                score = clampScore(state.score + moveScore(source.kind, target.kind, flipped)),
            )
    }

    /** Standard scoring never goes below zero; Vegas is allowed to. */
    private fun clampScore(value: Int): Int =
        if (scoreMode == ScoreMode.STANDARD) maxOf(0, value) else value

    private fun moveScore(from: PileKind, to: PileKind, flipped: Boolean): Int = when (scoreMode) {
        ScoreMode.NONE -> 0
        ScoreMode.VEGAS -> if (to == PileKind.FOUNDATION) 5 else if (from == PileKind.FOUNDATION) -5 else 0
        ScoreMode.STANDARD -> {
            var points = 0
            if (to == PileKind.FOUNDATION) points += 10
            if (from == PileKind.FOUNDATION) points -= 15
            if (from == PileKind.WASTE && to == PileKind.TABLEAU) points += 5
            if (flipped) points += 5
            points
        }
    }

    private fun applyDraw(state: GameState): GameState? {
        val stock = state[STOCK]
        if (stock.isEmpty) return null
        val count = minOf(drawCount, stock.size)
        val taken = stock.cards.subList(stock.size - count, stock.size).toList()
        val nextStock = stock.take(stock.size - count)
        val nextWaste = state[WASTE].plus(taken.reversed())
        return state
            .with(STOCK to nextStock, WASTE to nextWaste)
            .copy(moves = state.moves + 1)
    }

    private fun applyRecycle(state: GameState): GameState? {
        val stock = state[STOCK]
        val waste = state[WASTE]
        if (stock.isNotEmpty || waste.isEmpty) return null
        if (redeals >= 0 && state.passes >= redeals) return null
        val recycled = waste.cards.reversed()
        val score = when (scoreMode) {
            ScoreMode.STANDARD -> clampScore(state.score + if (drawCount == 1) -100 else -20)
            else -> state.score
        }
        return state
            .with(
                STOCK to Pile(PileKind.STOCK, recycled, faceUpFrom = recycled.size),
                WASTE to Pile(PileKind.WASTE),
            )
            .copy(passes = state.passes + 1, moves = state.moves + 1, score = score)
    }

    override fun canDraw(state: GameState): Boolean =
        state[STOCK].isNotEmpty || (state[WASTE].isNotEmpty && (redeals < 0 || state.passes < redeals))

    override fun tapTarget(state: GameState, pile: Int, index: Int): Move? {
        val source = state[pile]
        if (source.kind == PileKind.STOCK) {
            return if (source.isNotEmpty) Move.Draw else if (canDraw(state)) Move.Recycle else null
        }
        if (!canPickUp(state, pile, index)) return null
        val wholeColumn = source.kind == PileKind.TABLEAU && index == 0
        if (index == source.size - 1) {
            for (i in 0 until 4) {
                val foundation = FOUNDATION + i
                if (canDrop(state, pile, index, foundation)) return Move.cards(pile, index, foundation)
            }
        }
        // Prefer a column that already has cards; dropping a whole column onto an
        // empty one just shuffles it sideways.
        for (i in 0 until 7) {
            val column = TABLEAU + i
            if (state[column].isEmpty) continue
            if (canDrop(state, pile, index, column)) return Move.cards(pile, index, column)
        }
        if (!wholeColumn) {
            for (i in 0 until 7) {
                val column = TABLEAU + i
                if (state[column].isNotEmpty) continue
                if (canDrop(state, pile, index, column)) return Move.cards(pile, index, column)
            }
        }
        return null
    }

    override fun autoFinishMove(state: GameState): Move? {
        val sources = listOf(WASTE) + (0 until 7).map { TABLEAU + it }
        for (source in sources) {
            val top = state[source].size - 1
            if (top < 0) continue
            for (i in 0 until 4) {
                val foundation = FOUNDATION + i
                if (canDrop(state, source, top, foundation)) return Move.cards(source, top, foundation)
            }
        }
        return null
    }

    /** The subset of [autoFinishMove] that can never strand a lower card. */
    fun safeAutoPlay(state: GameState): Move? {
        val move = autoFinishMove(state) ?: return null
        val card = state[move.from].cards[move.index]
        val foundations = (0 until 4).map { state[FOUNDATION + it] }
        return if (isSafeToAutoPlay(foundations, card)) move else null
    }

    override fun hint(state: GameState): Move? {
        autoFinishMove(state)?.let { return it }
        // A move that uncovers a face-down card is worth more than one that does not.
        var fallback: Move? = null
        for (i in 0 until 7) {
            val from = TABLEAU + i
            val source = state[from]
            if (source.isEmpty) continue
            val start = source.faceUpFrom
            if (!canPickUp(state, from, start)) continue
            for (j in 0 until 7) {
                val to = TABLEAU + j
                if (!canDrop(state, from, start, to)) continue
                val uncovers = start > 0
                val move = Move.cards(from, start, to)
                if (uncovers) return move
                if (fallback == null && state[to].isNotEmpty) fallback = move
            }
        }
        val wasteTop = state[WASTE].size - 1
        if (wasteTop >= 0) {
            for (i in 0 until 7) {
                val to = TABLEAU + i
                if (canDrop(state, WASTE, wasteTop, to)) return Move.cards(WASTE, wasteTop, to)
            }
        }
        fallback?.let { return it }
        if (state[STOCK].isNotEmpty) return Move.Draw
        if (canDraw(state)) return Move.Recycle
        return null
    }

    override fun statusText(state: GameState): String {
        val stock = state[STOCK].size
        val waste = state[WASTE].size
        val built = (0 until 4).sumOf { state[FOUNDATION + it].size }
        return "Stock $stock · Waste $waste · Home $built/52"
    }

    companion object {
        const val STOCK = 0
        const val WASTE = 1
        const val FOUNDATION = 2
        const val TABLEAU = 6
    }
}
