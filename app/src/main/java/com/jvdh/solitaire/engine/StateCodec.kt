package com.jvdh.solitaire.engine

/**
 * A compact text form of a [GameState], used to keep a game in progress across
 * app restarts. Deliberately hand-rolled: the engine stays free of any
 * serialization dependency, and the format is easy to eyeball in a test.
 *
 * `v1;score;moves;passes;streak;kind:faceUpFrom:code,code|kind:faceUpFrom:...`
 */
object StateCodec {

    private const val VERSION = "v1"

    fun encode(state: GameState): String {
        val piles = state.piles.joinToString("|") { pile ->
            "${pile.kind.ordinal}:${pile.faceUpFrom}:${pile.cards.joinToString(",") { it.code.toString() }}"
        }
        return listOf(
            VERSION,
            state.score.toString(),
            state.moves.toString(),
            state.passes.toString(),
            state.streak.toString(),
            piles,
        ).joinToString(";")
    }

    fun decode(text: String): GameState? {
        val parts = text.split(";")
        if (parts.size != 6 || parts[0] != VERSION) return null
        return try {
            val piles = parts[5].split("|").map { encoded ->
                val fields = encoded.split(":")
                val cards = if (fields[2].isEmpty()) emptyList()
                else fields[2].split(",").map { Card(it.toInt()) }
                Pile(
                    kind = PileKind.entries[fields[0].toInt()],
                    cards = cards,
                    faceUpFrom = fields[1].toInt(),
                )
            }
            GameState(
                piles = piles,
                score = parts[1].toInt(),
                moves = parts[2].toInt(),
                passes = parts[3].toInt(),
                streak = parts[4].toInt(),
            )
        } catch (e: RuntimeException) {
            null
        }
    }
}
