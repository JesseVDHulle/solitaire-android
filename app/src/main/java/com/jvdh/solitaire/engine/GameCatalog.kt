package com.jvdh.solitaire.engine

import com.jvdh.solitaire.engine.games.FreeCell
import com.jvdh.solitaire.engine.games.Klondike
import com.jvdh.solitaire.engine.games.Pyramid
import com.jvdh.solitaire.engine.games.ScoreMode
import com.jvdh.solitaire.engine.games.Spider
import com.jvdh.solitaire.engine.games.TriPeaks

/** Every per-game rule the player can change from Settings. */
data class GameOptions(
    val klondikeDraw: Int = 1,
    val klondikeRedeals: Int = -1,
    val klondikeScoring: ScoreMode = ScoreMode.STANDARD,
    val spiderSuits: Int = 1,
    val freeCellCells: Int = 4,
    val triPeaksWrap: Boolean = true,
    val pyramidRedeals: Int = 2,
)

fun createGame(id: GameId, options: GameOptions = GameOptions()): SolitaireGame = when (id) {
    GameId.KLONDIKE -> Klondike(
        drawCount = options.klondikeDraw,
        redeals = options.klondikeRedeals,
        scoreMode = options.klondikeScoring,
    )
    GameId.SPIDER -> Spider(suitCount = options.spiderSuits)
    GameId.FREECELL -> FreeCell(cells = options.freeCellCells)
    GameId.TRIPEAKS -> TriPeaks(wrapAces = options.triPeaksWrap)
    GameId.PYRAMID -> Pyramid(redeals = options.pyramidRedeals)
}

/** One-line description shown on the game picker. */
fun blurbFor(id: GameId): String = when (id) {
    GameId.KLONDIKE -> "The classic. Build four suits up from the Ace."
    GameId.SPIDER -> "Ten columns, two decks. Assemble eight runs."
    GameId.FREECELL -> "Everything face up. Almost every deal is winnable."
    GameId.TRIPEAKS -> "Clear three peaks with one long chain."
    GameId.PYRAMID -> "Pair cards that add up to thirteen."
}
