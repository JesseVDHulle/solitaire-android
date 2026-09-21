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
import com.jvdh.solitaire.engine.isSuitedRun
import com.jvdh.solitaire.engine.spiderDeck
import kotlin.random.Random

/**
 * Spider: ten columns built downwards in rank, but only same-suit runs travel
 * together. A finished King-to-Ace run in one suit leaves the table.
 *
 * @param suitCount 1, 2 or 4 -- the usual difficulty dial.
 */
class Spider(private val suitCount: Int = 4) : SolitaireGame {

    override val id = GameId.SPIDER

    override val variantLabel: String =
        when (suitCount) {
            1 -> "1 suit · easy"
            2 -> "2 suits · medium"
            else -> "4 suits · hard"
        }

    override val layout: TableLayout = run {
        val step = 1.06f
        val slots = ArrayList<Slot>(19)
        slots += Slot(STOCK, 9 * step, 0f)
        for (i in 0 until 8) slots += Slot(FOUNDATION + i, i * step * 0.42f, 0f)
        for (i in 0 until 10) slots += Slot(TABLEAU + i, i * step, 1.22f, Fan.DOWN)
        TableLayout(slots, width = 9 * step + 1f, height = 5.1f)
    }

    override fun deal(rng: Random): GameState {
        val deck = spiderDeck(suitCount).shuffled(rng).toMutableList()
        val piles = ArrayList<Pile>(19)
        piles += Pile(PileKind.STOCK)
        repeat(8) { piles += Pile(PileKind.FOUNDATION) }
        val tableau = ArrayList<Pile>(10)
        for (column in 0 until 10) {
            val count = if (column < 4) 6 else 5
            val cards = ArrayList<Card>(count)
            repeat(count) { cards += deck.removeAt(deck.size - 1) }
            tableau += Pile(PileKind.TABLEAU, cards, faceUpFrom = count - 1)
        }
        piles += tableau
        val state = GameState(piles, score = 500)
        return state.with(STOCK to Pile(PileKind.STOCK, deck.toList(), faceUpFrom = deck.size))
    }

    override fun isWon(state: GameState): Boolean =
        (0 until 8).all { state[FOUNDATION + it].size == 13 }

    override fun canPickUp(state: GameState, pile: Int, index: Int): Boolean {
        val source = state[pile]
        if (source.kind != PileKind.TABLEAU) return false
        if (index !in source.cards.indices || !source.isFaceUp(index)) return false
        return isSuitedRun(source.cards, index)
    }

    override fun canDrop(state: GameState, pile: Int, index: Int, to: Int): Boolean {
        if (to == pile) return false
        if (!canPickUp(state, pile, index)) return false
        val target = state[to]
        if (target.kind != PileKind.TABLEAU) return false
        val card = state[pile].cards[index]
        val top = target.top ?: return true
        return top.rank == card.rank + 1
    }

    override fun apply(state: GameState, move: Move): GameState? = when (move.kind) {
        MoveKind.CARDS -> applyCards(state, move)
        MoveKind.DEAL -> applyDeal(state)
        else -> null
    }

    private fun applyCards(state: GameState, move: Move): GameState? {
        if (!canDrop(state, move.from, move.index, move.to)) return null
        val source = state[move.from]
        val moving = source.cards.subList(move.index, source.size).toList()
        val next = state
            .with(
                move.from to source.take(move.index).revealTop(),
                move.to to state[move.to].plus(moving),
            )
            .copy(moves = state.moves + 1, score = state.score - 1)
        return collectRuns(next)
    }

    private fun applyDeal(state: GameState): GameState? {
        val stock = state[STOCK]
        if (stock.isEmpty) return null
        // The rule everyone forgets: you may not deal onto an empty column.
        if ((0 until 10).any { state[TABLEAU + it].isEmpty }) return null
        var remaining = stock.cards
        var next = state
        for (i in 0 until 10) {
            if (remaining.isEmpty()) break
            val card = remaining.last()
            remaining = remaining.subList(0, remaining.size - 1)
            val column = TABLEAU + i
            next = next.with(column to next[column].plus(listOf(card)))
        }
        next = next
            .with(STOCK to Pile(PileKind.STOCK, remaining.toList(), faceUpFrom = remaining.size))
            .copy(moves = state.moves + 1)
        return collectRuns(next)
    }

    /** Sweeps any completed King-to-Ace suited run into a free foundation. */
    private fun collectRuns(state: GameState): GameState {
        var next = state
        var changed = true
        while (changed) {
            changed = false
            for (i in 0 until 10) {
                val column = TABLEAU + i
                val pile = next[column]
                if (pile.size < 13) continue
                val start = pile.size - 13
                if (!pile.isFaceUp(start)) continue
                val run = pile.cards.subList(start, pile.size)
                if (run.first().rank != KING || run.last().rank != ACE) continue
                if (!isSuitedRun(pile.cards, start)) continue
                val foundation = (0 until 8).map { FOUNDATION + it }
                    .firstOrNull { next[it].isEmpty } ?: continue
                next = next
                    .with(
                        column to pile.take(start).revealTop(),
                        foundation to Pile(PileKind.FOUNDATION, run.toList()),
                    )
                    .copy(score = next.score + 100)
                changed = true
            }
        }
        return next
    }

    override fun canDraw(state: GameState): Boolean = state[STOCK].isNotEmpty

    override fun tapTarget(state: GameState, pile: Int, index: Int): Move? {
        if (state[pile].kind == PileKind.STOCK) return Move.Deal
        if (!canPickUp(state, pile, index)) return null
        val card = state[pile].cards[index]
        val candidates = (0 until 10).map { TABLEAU + it }.filter { canDrop(state, pile, index, it) }
        if (candidates.isEmpty()) return null
        // Landing on a matching suit keeps the run together, so prefer that;
        // an empty column is the last resort because it is a scarce resource.
        val suited = candidates.firstOrNull { state[it].top?.suit == card.suit }
        val occupied = candidates.firstOrNull { state[it].isNotEmpty }
        val target = suited ?: occupied ?: candidates.first()
        if (state[target].isEmpty && index == 0) return null
        return Move.cards(pile, index, target)
    }

    override fun autoFinishMove(state: GameState): Move? = null

    override fun hint(state: GameState): Move? {
        var fallback: Move? = null
        for (i in 0 until 10) {
            val from = TABLEAU + i
            val source = state[from]
            if (source.isEmpty) continue
            for (index in source.faceUpFrom until source.size) {
                if (!canPickUp(state, from, index)) continue
                for (j in 0 until 10) {
                    val to = TABLEAU + j
                    if (!canDrop(state, from, index, to)) continue
                    val card = source.cards[index]
                    val suited = state[to].top?.suit == card.suit
                    val uncovers = index > 0 && !source.isFaceUp(index - 1)
                    if (suited && uncovers) return Move.cards(from, index, to)
                    if (fallback == null && state[to].isNotEmpty) fallback = Move.cards(from, index, to)
                }
            }
        }
        fallback?.let { return it }
        return if (apply(state, Move.Deal) != null) Move.Deal else null
    }

    override fun statusText(state: GameState): String {
        val deals = state[STOCK].size / 10
        val sets = (0 until 8).count { state[FOUNDATION + it].isNotEmpty }
        return "Deals left $deals · Sets $sets/8"
    }

    companion object {
        const val STOCK = 0
        const val FOUNDATION = 1
        const val TABLEAU = 9
    }
}
