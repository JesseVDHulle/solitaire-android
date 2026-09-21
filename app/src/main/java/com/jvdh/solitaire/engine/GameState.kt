package com.jvdh.solitaire.engine

enum class PileKind {
    /** Face-down draw pile. */
    STOCK,

    /** Face-up pile drawn cards land on. Doubles as the discard in Pyramid. */
    WASTE,

    /** Where cards are collected to win. */
    FOUNDATION,

    /** The main playing columns. */
    TABLEAU,

    /** A FreeCell holding slot: one card, always face up. */
    FREE_CELL,

    /** A single-card slot in a fixed figure, as in TriPeaks and Pyramid. */
    RESERVE,
}

/**
 * One stack of cards. [faceUpFrom] is the first index that is face up, so a pile
 * with `faceUpFrom == cards.size` is entirely face down and `0` is entirely face
 * up.
 */
data class Pile(
    val kind: PileKind,
    val cards: List<Card> = emptyList(),
    val faceUpFrom: Int = 0,
) {
    val size: Int get() = cards.size
    val isEmpty: Boolean get() = cards.isEmpty()
    val isNotEmpty: Boolean get() = cards.isNotEmpty()
    val top: Card? get() = cards.lastOrNull()

    fun isFaceUp(index: Int): Boolean = index >= faceUpFrom
    fun cardAt(index: Int): Card? = cards.getOrNull(index)

    /** The pile with [added] appended face up. */
    fun plus(added: List<Card>): Pile =
        copy(cards = cards + added, faceUpFrom = minOf(faceUpFrom, cards.size))

    /** The pile with everything from [index] removed, keeping the face-up mark sane. */
    fun take(index: Int): Pile =
        copy(cards = cards.subList(0, index), faceUpFrom = minOf(faceUpFrom, index))

    /** Turns the top card face up, if there is one. */
    fun revealTop(): Pile =
        if (cards.isEmpty()) this else copy(faceUpFrom = minOf(faceUpFrom, cards.size - 1))
}

/** A complete, immutable snapshot of a game. Undo simply keeps older snapshots. */
data class GameState(
    val piles: List<Pile>,
    val score: Int = 0,
    val moves: Int = 0,
    /** How many times the stock has been recycled. */
    val passes: Int = 0,
    /** Consecutive-card run, used by TriPeaks scoring. */
    val streak: Int = 0,
) {
    operator fun get(index: Int): Pile = piles[index]

    fun with(vararg updates: Pair<Int, Pile>): GameState {
        val next = piles.toMutableList()
        for ((index, pile) in updates) next[index] = pile
        return copy(piles = next)
    }

    fun indicesOf(kind: PileKind): List<Int> = piles.indices.filter { piles[it].kind == kind }
}

enum class MoveKind {
    /** Move one or more cards between piles. */
    CARDS,

    /** Turn cards from the stock onto the waste. */
    DRAW,

    /** Put the waste back under the stock. */
    RECYCLE,

    /** Spider's "deal a card to every column". */
    DEAL,
}

/**
 * A single player action. [index] is the position within [from] that the move
 * starts at, so a Klondike sequence move and a single-card move share a shape.
 */
data class Move(
    val kind: MoveKind,
    val from: Int = -1,
    val index: Int = -1,
    val to: Int = -1,
) {
    companion object {
        val Draw = Move(MoveKind.DRAW)
        val Recycle = Move(MoveKind.RECYCLE)
        val Deal = Move(MoveKind.DEAL)
        fun cards(from: Int, index: Int, to: Int) = Move(MoveKind.CARDS, from, index, to)
    }
}
