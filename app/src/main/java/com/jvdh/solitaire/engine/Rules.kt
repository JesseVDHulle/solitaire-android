package com.jvdh.solitaire.engine

/** Shared building blocks for the tableau-and-foundation games. */

/** Descending by one, alternating colours: the Klondike / FreeCell sequence. */
fun isAlternatingRun(cards: List<Card>, from: Int): Boolean {
    for (i in from until cards.size - 1) {
        val upper = cards[i]
        val lower = cards[i + 1]
        if (upper.rank != lower.rank + 1 || upper.isRed == lower.isRed) return false
    }
    return true
}

/** Descending by one in a single suit: the Spider sequence. */
fun isSuitedRun(cards: List<Card>, from: Int): Boolean {
    for (i in from until cards.size - 1) {
        val upper = cards[i]
        val lower = cards[i + 1]
        if (upper.rank != lower.rank + 1 || upper.suit != lower.suit) return false
    }
    return true
}

/** Ace upwards in suit, the usual foundation. */
fun acceptsOnFoundation(foundation: Pile, card: Card): Boolean {
    val top = foundation.top ?: return card.rank == ACE
    return top.suit == card.suit && top.rank == card.rank - 1
}

/** King onto empty, otherwise down one and alternating colour. */
fun acceptsOnAlternatingTableau(tableau: Pile, card: Card): Boolean {
    val top = tableau.top ?: return card.rank == KING
    return top.rank == card.rank + 1 && top.isRed != card.isRed
}

/** The highest rank each suit has reached on the foundations, 0 when none. */
fun foundationRanks(foundations: List<Pile>): Map<Suit, Int> =
    Suit.entries.associateWith { suit ->
        foundations.firstOrNull { it.top?.suit == suit }?.top?.rank ?: 0
    }

/**
 * True when [card] can never be needed to hold a lower card of the other
 * colour, so sending it to a foundation cannot cost the player anything. Aces
 * and twos are always safe; anything else needs both opposite-colour
 * foundations to have reached at least one rank below it.
 */
fun isSafeToAutoPlay(foundations: List<Pile>, card: Card): Boolean {
    if (card.rank <= 2) return true
    val ranks = foundationRanks(foundations)
    return Suit.entries
        .filter { it.isRed != card.isRed }
        .all { (ranks[it] ?: 0) >= card.rank - 1 }
}
