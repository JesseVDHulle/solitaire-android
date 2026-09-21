package com.jvdh.solitaire.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jvdh.solitaire.engine.GameId
import com.jvdh.solitaire.engine.GameState
import com.jvdh.solitaire.engine.StateCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** A game left half-played, so the player can come back to it. */
data class SavedGame(
    val state: GameState,
    /** The deal as it was dealt, so "restart" can put it back. */
    val dealStart: GameState,
    val elapsedSeconds: Long,
    val variantLabel: String,
)

/** Lifetime figures for one game. */
data class GameStats(
    val played: Int = 0,
    val won: Int = 0,
    val bestScore: Int = 0,
    val bestTimeSeconds: Long = 0,
    val fewestMoves: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
) {
    val winRate: Int get() = if (played == 0) 0 else (won * 100) / played
}

private val Context.gameStore: DataStore<Preferences> by preferencesDataStore("games")

/** Stores the game in progress for each solitaire, plus lifetime statistics. */
class GameStore(private val context: Context) {

    fun savedGame(id: GameId): Flow<SavedGame?> = context.gameStore.data.map { prefs ->
        val encoded = prefs[stateKey(id)] ?: return@map null
        val state = StateCodec.decode(encoded) ?: return@map null
        SavedGame(
            state = state,
            dealStart = prefs[dealKey(id)]?.let { StateCodec.decode(it) } ?: state,
            elapsedSeconds = prefs[elapsedKey(id)] ?: 0L,
            variantLabel = prefs[variantKey(id)].orEmpty(),
        )
    }

    suspend fun save(
        id: GameId,
        state: GameState,
        dealStart: GameState,
        elapsedSeconds: Long,
        variantLabel: String,
    ) {
        context.gameStore.edit { prefs ->
            prefs[stateKey(id)] = StateCodec.encode(state)
            prefs[dealKey(id)] = StateCodec.encode(dealStart)
            prefs[elapsedKey(id)] = elapsedSeconds
            prefs[variantKey(id)] = variantLabel
        }
    }

    /** The games that have a deal waiting to be picked up again. */
    val allSaved: Flow<Set<GameId>> = context.gameStore.data.map { prefs ->
        GameId.entries.filterTo(LinkedHashSet()) { prefs[stateKey(it)] != null }
    }

    suspend fun clear(id: GameId) {
        context.gameStore.edit { prefs ->
            prefs.remove(stateKey(id))
            prefs.remove(dealKey(id))
            prefs.remove(elapsedKey(id))
            prefs.remove(variantKey(id))
        }
    }

    val allStats: Flow<Map<GameId, GameStats>> = context.gameStore.data.map { prefs ->
        GameId.entries.associateWith { id ->
            GameStats(
                played = prefs[playedKey(id)] ?: 0,
                won = prefs[wonKey(id)] ?: 0,
                bestScore = prefs[bestScoreKey(id)] ?: 0,
                bestTimeSeconds = prefs[bestTimeKey(id)] ?: 0L,
                fewestMoves = prefs[fewestMovesKey(id)] ?: 0,
                currentStreak = prefs[streakKey(id)] ?: 0,
                bestStreak = prefs[bestStreakKey(id)] ?: 0,
            )
        }
    }

    /** Counts a deal as started. Called once, when the cards are dealt. */
    suspend fun recordDeal(id: GameId) {
        context.gameStore.edit { prefs ->
            prefs[playedKey(id)] = (prefs[playedKey(id)] ?: 0) + 1
        }
    }

    suspend fun recordWin(id: GameId, score: Int, seconds: Long, moves: Int) {
        context.gameStore.edit { prefs ->
            prefs[wonKey(id)] = (prefs[wonKey(id)] ?: 0) + 1
            prefs[bestScoreKey(id)] = maxOf(prefs[bestScoreKey(id)] ?: Int.MIN_VALUE, score)
            val best = prefs[bestTimeKey(id)] ?: 0L
            prefs[bestTimeKey(id)] = if (best == 0L) seconds else minOf(best, seconds)
            val fewest = prefs[fewestMovesKey(id)] ?: 0
            prefs[fewestMovesKey(id)] = if (fewest == 0) moves else minOf(fewest, moves)
            val streak = (prefs[streakKey(id)] ?: 0) + 1
            prefs[streakKey(id)] = streak
            prefs[bestStreakKey(id)] = maxOf(prefs[bestStreakKey(id)] ?: 0, streak)
        }
    }

    /** A deal abandoned or restarted after a move ends the winning streak. */
    suspend fun recordLoss(id: GameId) {
        context.gameStore.edit { prefs -> prefs[streakKey(id)] = 0 }
    }

    suspend fun resetStats(id: GameId) {
        context.gameStore.edit { prefs ->
            listOf(
                playedKey(id), wonKey(id), bestScoreKey(id), fewestMovesKey(id),
                streakKey(id), bestStreakKey(id),
            ).forEach { prefs.remove(it) }
            prefs.remove(bestTimeKey(id))
        }
    }

    private fun stateKey(id: GameId) = stringPreferencesKey("state_${id.name}")
    private fun dealKey(id: GameId) = stringPreferencesKey("deal_${id.name}")
    private fun elapsedKey(id: GameId) = longPreferencesKey("elapsed_${id.name}")
    private fun variantKey(id: GameId) = stringPreferencesKey("variant_${id.name}")
    private fun playedKey(id: GameId) = intPreferencesKey("played_${id.name}")
    private fun wonKey(id: GameId) = intPreferencesKey("won_${id.name}")
    private fun bestScoreKey(id: GameId) = intPreferencesKey("best_score_${id.name}")
    private fun bestTimeKey(id: GameId) = longPreferencesKey("best_time_${id.name}")
    private fun fewestMovesKey(id: GameId) = intPreferencesKey("fewest_moves_${id.name}")
    private fun streakKey(id: GameId) = intPreferencesKey("streak_${id.name}")
    private fun bestStreakKey(id: GameId) = intPreferencesKey("best_streak_${id.name}")
}
