package com.jvdh.solitaire.engine

enum class Suit(val symbol: String, val isRed: Boolean) {
    SPADES("♠", false),
    HEARTS("♥", true),
    CLUBS("♣", false),
    DIAMONDS("♦", true),
}

const val ACE = 1
const val JACK = 11
const val QUEEN = 12
const val KING = 13

private val RANK_LABELS =
    arrayOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")

/**
 * A single physical card.
 *
 * The [code] packs a per-deal unique id together with suit and rank, so that two
 * identical cards from different decks (Spider deals two, and its one- and
 * two-suit variants repeat suits eight and four times over) still compare and
 * animate as distinct cards.
 *
 * Bit layout: `uid shl 6 | suit shl 4 | rank`.
 */
@JvmInline
value class Card(val code: Int) {
    constructor(uid: Int, suit: Suit, rank: Int) : this((uid shl 6) or (suit.ordinal shl 4) or rank)

    val rank: Int get() = code and 0xF
    val suit: Suit get() = Suit.entries[(code shr 4) and 0x3]
    val uid: Int get() = code shr 6

    val isRed: Boolean get() = suit.isRed
    val rankLabel: String get() = RANK_LABELS[rank - 1]

    override fun toString(): String = "$rankLabel${suit.symbol}"
}

/** `decks` full 52-card decks, in order. Shuffle before dealing. */
fun standardDeck(decks: Int = 1): List<Card> {
    val out = ArrayList<Card>(decks * 52)
    var uid = 0
    repeat(decks) {
        for (suit in Suit.entries) {
            for (rank in ACE..KING) out += Card(uid++, suit, rank)
        }
    }
    return out
}

/**
 * The 104-card Spider deck. [suitCount] of 1, 2 or 4 repeats the chosen suits
 * until eight full ranks worth of cards exist.
 */
fun spiderDeck(suitCount: Int): List<Card> {
    val suits = when (suitCount) {
        1 -> listOf(Suit.SPADES)
        2 -> listOf(Suit.SPADES, Suit.HEARTS)
        else -> Suit.entries.toList()
    }
    val copies = 8 / suits.size
    val out = ArrayList<Card>(104)
    var uid = 0
    for (suit in suits) {
        repeat(copies) {
            for (rank in ACE..KING) out += Card(uid++, suit, rank)
        }
    }
    return out
}
