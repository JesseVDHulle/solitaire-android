package com.jvdh.solitaire.engine

/** How the cards of a pile are spread out. */
enum class Fan {
    /** Only the top card is visible. */
    NONE,

    /** Spread downwards; the usual tableau column. */
    DOWN,

    /** Spread to the right, as Klondike's three-card waste. */
    RIGHT,
}

/**
 * Where a pile sits on the table.
 *
 * [x] and [y] are in card units: 1.0 on x is one card width, 1.0 on y is one
 * card height. Gaps are baked into the numbers by each game, which lets
 * overlapping figures (the TriPeaks peaks, the Pyramid) and plain rows share one
 * description. The renderer scales the whole thing to fit.
 *
 * [fanLimit] caps how many cards a RIGHT fan shows; DOWN fans show everything and
 * are squeezed together as they grow.
 */
data class Slot(
    val pile: Int,
    val x: Float,
    val y: Float,
    val fan: Fan = Fan.NONE,
    val fanLimit: Int = 0,
)

/**
 * The table for one game. [width] and [height] are the extent in card units,
 * measured with every pile at its dealt size; a growing tableau is compressed
 * rather than allowed to overflow.
 */
data class TableLayout(
    val slots: List<Slot>,
    val width: Float,
    val height: Float,
) {
    fun slotFor(pile: Int): Slot? = slots.firstOrNull { it.pile == pile }
}
