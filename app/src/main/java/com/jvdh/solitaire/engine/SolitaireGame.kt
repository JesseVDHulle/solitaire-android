package com.jvdh.solitaire.engine

import kotlin.random.Random

enum class GameId(val title: String) {
    KLONDIKE("Klondike"),
    SPIDER("Spider"),
    FREECELL("FreeCell"),
    TRIPEAKS("TriPeaks"),
    PYRAMID("Pyramid"),
}

/** What the tableau of a finished game looks like, for the win check and stats. */
data class Outcome(val won: Boolean, val stuck: Boolean)

/**
 * The rules of one solitaire. Implementations are stateless: every method takes
 * the state it should reason about and returns a new one, which keeps undo,
 * hints and the auto-player to a single code path.
 */
interface SolitaireGame {
    val id: GameId

    /** Pile positions for the renderer. Constant for the life of a game. */
    val layout: TableLayout

    /** Human-readable options line, e.g. "Draw 3 · 2 suits". */
    val variantLabel: String

    fun deal(rng: Random): GameState

    fun isWon(state: GameState): Boolean

    /** True when the player may lift the cards of [pile] from [index] upward. */
    fun canPickUp(state: GameState, pile: Int, index: Int): Boolean

    /** True when the lifted cards may be dropped on [to]. */
    fun canDrop(state: GameState, pile: Int, index: Int, to: Int): Boolean

    /** Applies [move], or returns null when it is not legal. */
    fun apply(state: GameState, move: Move): GameState?

    /**
     * What a tap on that card should do: the destination the player most likely
     * wants. Null means "nothing obvious", which the caller turns into a
     * selection so a second tap can pick the target.
     */
    fun tapTarget(state: GameState, pile: Int, index: Int): Move?

    /** The next move an auto-finish should make, or null when it should stop. */
    fun autoFinishMove(state: GameState): Move?

    /** A move worth suggesting, or null when the player is stuck. */
    fun hint(state: GameState): Move?

    /** True when the stock can still be drawn from or recycled. */
    fun canDraw(state: GameState): Boolean

    /** Short line under the board: stock count, cells free, sets built. */
    fun statusText(state: GameState): String

    /** True when no legal move remains, so the deal is lost. */
    fun isStuck(state: GameState): Boolean = !isWon(state) && allMoves(state).isEmpty()

    /** Every legal move in [state]. The default walks all pile pairs. */
    fun allMoves(state: GameState): List<Move> {
        val out = ArrayList<Move>()
        if (canDraw(state)) {
            apply(state, Move.Draw)?.let { out += Move.Draw }
            apply(state, Move.Recycle)?.let { out += Move.Recycle }
            apply(state, Move.Deal)?.let { out += Move.Deal }
        }
        for (from in state.piles.indices) {
            val pile = state[from]
            for (index in pile.cards.indices) {
                if (!canPickUp(state, from, index)) continue
                for (to in state.piles.indices) {
                    if (to == from) continue
                    if (canDrop(state, from, index, to)) out += Move.cards(from, index, to)
                }
            }
        }
        return out
    }
}

/** Ranks are adjacent, treating Ace as both below 2 and above King. */
fun ranksWrapAdjacent(a: Int, b: Int): Boolean {
    val diff = kotlin.math.abs(a - b)
    return diff == 1 || diff == KING - ACE
}
