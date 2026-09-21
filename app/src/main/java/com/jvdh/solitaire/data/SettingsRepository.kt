package com.jvdh.solitaire.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jvdh.solitaire.engine.GameOptions
import com.jvdh.solitaire.engine.games.ScoreMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

/** Reads and writes [Settings]; every preference has a sensible default. */
class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.settingsStore.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        context.settingsStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs.writeEnum(Keys.THEME, next.themeMode)
            prefs.writeEnum(Keys.TABLE, next.tableTheme)
            prefs.writeEnum(Keys.BACK_STYLE, next.cardBackStyle)
            prefs.writeEnum(Keys.BACK_COLOUR, next.cardBackColor)
            prefs.writeEnum(Keys.FACE_STYLE, next.faceStyle)
            prefs.writeEnum(Keys.ANIMATION, next.animationSpeed)
            prefs.writeEnum(Keys.SPACING, next.cardSpacing)
            prefs[Keys.FOUR_COLOUR] = next.fourColourDeck
            prefs[Keys.LEFT_HANDED] = next.leftHanded
            prefs[Keys.TAP_TO_MOVE] = next.tapToMove
            prefs[Keys.AUTO_SAFE] = next.autoPlaySafe
            prefs[Keys.AUTO_FINISH] = next.autoFinishPrompt
            prefs[Keys.CARD_SCALE] = next.cardScale
            prefs[Keys.HAPTICS] = next.haptics
            prefs[Keys.SHOW_TIMER] = next.showTimer
            prefs[Keys.SHOW_SCORE] = next.showScore
            prefs[Keys.CONFIRM_RESTART] = next.confirmRestart
            prefs[Keys.UNLIMITED_UNDO] = next.unlimitedUndo
            prefs[Keys.KLONDIKE_DRAW] = next.gameOptions.klondikeDraw
            prefs[Keys.KLONDIKE_REDEALS] = next.gameOptions.klondikeRedeals
            prefs[Keys.KLONDIKE_SCORING] = next.gameOptions.klondikeScoring.name
            prefs[Keys.SPIDER_SUITS] = next.gameOptions.spiderSuits
            prefs[Keys.FREECELL_CELLS] = next.gameOptions.freeCellCells
            prefs[Keys.TRIPEAKS_WRAP] = next.gameOptions.triPeaksWrap
            prefs[Keys.PYRAMID_REDEALS] = next.gameOptions.pyramidRedeals
        }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val TABLE = stringPreferencesKey("table")
        val BACK_STYLE = stringPreferencesKey("back_style")
        val BACK_COLOUR = stringPreferencesKey("back_colour")
        val FACE_STYLE = stringPreferencesKey("face_style")
        val ANIMATION = stringPreferencesKey("animation")
        val SPACING = stringPreferencesKey("spacing")
        val FOUR_COLOUR = booleanPreferencesKey("four_colour")
        val LEFT_HANDED = booleanPreferencesKey("left_handed")
        val TAP_TO_MOVE = booleanPreferencesKey("tap_to_move")
        val AUTO_SAFE = booleanPreferencesKey("auto_safe")
        val AUTO_FINISH = booleanPreferencesKey("auto_finish")
        val CARD_SCALE = floatPreferencesKey("card_scale")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SHOW_TIMER = booleanPreferencesKey("show_timer")
        val SHOW_SCORE = booleanPreferencesKey("show_score")
        val CONFIRM_RESTART = booleanPreferencesKey("confirm_restart")
        val UNLIMITED_UNDO = booleanPreferencesKey("unlimited_undo")
        val KLONDIKE_DRAW = intPreferencesKey("klondike_draw")
        val KLONDIKE_REDEALS = intPreferencesKey("klondike_redeals")
        val KLONDIKE_SCORING = stringPreferencesKey("klondike_scoring")
        val SPIDER_SUITS = intPreferencesKey("spider_suits")
        val FREECELL_CELLS = intPreferencesKey("freecell_cells")
        val TRIPEAKS_WRAP = booleanPreferencesKey("tripeaks_wrap")
        val PYRAMID_REDEALS = intPreferencesKey("pyramid_redeals")
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            themeMode = readEnum(Keys.THEME, defaults.themeMode),
            tableTheme = readEnum(Keys.TABLE, defaults.tableTheme),
            cardBackStyle = readEnum(Keys.BACK_STYLE, defaults.cardBackStyle),
            cardBackColor = readEnum(Keys.BACK_COLOUR, defaults.cardBackColor),
            faceStyle = readEnum(Keys.FACE_STYLE, defaults.faceStyle),
            fourColourDeck = this[Keys.FOUR_COLOUR] ?: defaults.fourColourDeck,
            leftHanded = this[Keys.LEFT_HANDED] ?: defaults.leftHanded,
            tapToMove = this[Keys.TAP_TO_MOVE] ?: defaults.tapToMove,
            autoPlaySafe = this[Keys.AUTO_SAFE] ?: defaults.autoPlaySafe,
            autoFinishPrompt = this[Keys.AUTO_FINISH] ?: defaults.autoFinishPrompt,
            animationSpeed = readEnum(Keys.ANIMATION, defaults.animationSpeed),
            cardSpacing = readEnum(Keys.SPACING, defaults.cardSpacing),
            cardScale = this[Keys.CARD_SCALE] ?: defaults.cardScale,
            haptics = this[Keys.HAPTICS] ?: defaults.haptics,
            showTimer = this[Keys.SHOW_TIMER] ?: defaults.showTimer,
            showScore = this[Keys.SHOW_SCORE] ?: defaults.showScore,
            confirmRestart = this[Keys.CONFIRM_RESTART] ?: defaults.confirmRestart,
            unlimitedUndo = this[Keys.UNLIMITED_UNDO] ?: defaults.unlimitedUndo,
            gameOptions = GameOptions(
                klondikeDraw = this[Keys.KLONDIKE_DRAW] ?: 1,
                klondikeRedeals = this[Keys.KLONDIKE_REDEALS] ?: -1,
                klondikeScoring = enumOrDefault(this[Keys.KLONDIKE_SCORING], ScoreMode.STANDARD),
                spiderSuits = this[Keys.SPIDER_SUITS] ?: 1,
                freeCellCells = this[Keys.FREECELL_CELLS] ?: 4,
                triPeaksWrap = this[Keys.TRIPEAKS_WRAP] ?: true,
                pyramidRedeals = this[Keys.PYRAMID_REDEALS] ?: 2,
            ),
        )
    }
}

private inline fun <reified T : Enum<T>> Preferences.readEnum(
    key: Preferences.Key<String>,
    fallback: T,
): T = enumOrDefault(this[key], fallback)

private fun <T : Enum<T>> MutablePreferences.writeEnum(key: Preferences.Key<String>, value: T) {
    this[key] = value.name
}

internal inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback
