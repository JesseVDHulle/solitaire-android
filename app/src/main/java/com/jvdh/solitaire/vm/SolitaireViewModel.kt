package com.jvdh.solitaire.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jvdh.solitaire.data.GameStats
import com.jvdh.solitaire.data.GameStore
import com.jvdh.solitaire.data.Settings
import com.jvdh.solitaire.data.SettingsRepository
import com.jvdh.solitaire.engine.GameId
import com.jvdh.solitaire.engine.GameState
import com.jvdh.solitaire.engine.Move
import com.jvdh.solitaire.engine.MoveKind
import com.jvdh.solitaire.engine.SolitaireGame
import com.jvdh.solitaire.engine.createGame
import com.jvdh.solitaire.engine.games.FreeCell
import com.jvdh.solitaire.engine.games.Klondike
import com.jvdh.solitaire.ui.Selection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Why the board is showing a banner. */
enum class Banner { NONE, WON, STUCK }

/**
 * Holds the game in progress, the undo history and the clock, and mediates
 * between the gesture layer and the rules engine.
 */
class SolitaireViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepository = SettingsRepository(app)
    private val store = GameStore(app)

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val stats: StateFlow<Map<GameId, GameStats>> = store.allStats
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Which games have a deal waiting to be resumed. */
    val resumable: StateFlow<Set<GameId>> = store.allSaved
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    var game: SolitaireGame? by mutableStateOf(null)
        private set
    var state: GameState? by mutableStateOf(null)
        private set
    var selection: Selection? by mutableStateOf(null)
        private set
    var hint: Move? by mutableStateOf(null)
        private set
    var banner: Banner by mutableStateOf(Banner.NONE)
        private set
    var elapsedSeconds: Long by mutableStateOf(0L)
        private set
    var autoFinishing: Boolean by mutableStateOf(false)
        private set

    /** True when the Finish button would actually carry the game home. */
    var autoFinishAvailable: Boolean by mutableStateOf(false)
        private set

    private val undoStack = ArrayDeque<GameState>()
    private val redoStack = ArrayDeque<GameState>()

    val canUndo: Boolean get() = undoStack.isNotEmpty() && !autoFinishing
    val canRedo: Boolean get() = redoStack.isNotEmpty() && !autoFinishing

    private var timerJob: Job? = null
    private var saveJob: Job? = null
    private var autoJob: Job? = null
    private var dealCounted = false
    private var dealStart: GameState? = null

    // ------------------------------------------------------------- lifecycle

    /** Opens [id], resuming the saved deal when there is one. */
    fun open(id: GameId) {
        viewModelScope.launch {
            val current = settings.first()
            val fresh = createGame(id, current.gameOptions)
            val saved = store.savedGame(id).first()
            val resumed = saved?.takeIf { it.variantLabel == fresh.variantLabel }
            game = fresh
            undoStack.clear()
            redoStack.clear()
            selection = null
            hint = null
            if (resumed != null) {
                setState(resumed.state)
                dealStart = resumed.dealStart
                elapsedSeconds = resumed.elapsedSeconds
                dealCounted = true
            } else {
                val dealt = fresh.deal(Random(System.nanoTime()))
                setState(dealt)
                dealStart = dealt
                elapsedSeconds = 0L
                dealCounted = false
                store.clear(id)
            }
            banner = if (state?.let { fresh.isWon(it) } == true) Banner.WON else Banner.NONE
            startTimer()
        }
    }

    fun close() {
        stopTimer()
        autoJob?.cancel()
        autoFinishing = false
        persistNow()
        game = null
        setState(null)
    }

    /** Deals again, counting the abandoned deal as a loss if it was under way. */
    fun newDeal() {
        val current = game ?: return
        viewModelScope.launch {
            val previous = state
            if (dealCounted && previous != null && previous.moves > 0 && !current.isWon(previous)) {
                store.recordLoss(current.id)
            }
            val options = settings.first().gameOptions
            val fresh = createGame(current.id, options)
            val dealt = fresh.deal(Random(System.nanoTime()))
            game = fresh
            setState(dealt)
            dealStart = dealt
            undoStack.clear()
            redoStack.clear()
            selection = null
            hint = null
            banner = Banner.NONE
            elapsedSeconds = 0L
            dealCounted = false
            store.clear(fresh.id)
            startTimer()
        }
    }

    /** Puts the same deal back the way it came out of the shuffle. */
    fun restartDeal() {
        val original = dealStart ?: return
        setState(original)
        undoStack.clear()
        redoStack.clear()
        selection = null
        hint = null
        banner = Banner.NONE
        elapsedSeconds = 0L
        startTimer()
        scheduleSave()
    }

    // ------------------------------------------------------------- gameplay

    /** A tap on a card, or on an empty pile when [index] is -1. */
    fun onTap(pile: Int, index: Int) {
        val current = game ?: return
        val now = state ?: return
        if (autoFinishing) return
        hint = null

        val held = selection
        if (held != null) {
            // A second tap completes the pair or the move the first tap started.
            if (held.pile != pile && current.canDrop(now, held.pile, held.index, pile)) {
                selection = null
                play(Move.cards(held.pile, held.index, pile))
                return
            }
            if (held.pile == pile && held.index == index) {
                selection = null
                return
            }
        }

        if (index < 0) {
            // An empty pile: only the stock does anything on its own.
            val move = current.tapTarget(now, pile, 0)
            if (move != null) {
                selection = null
                play(move)
            } else {
                selection = null
            }
            return
        }

        val settingsNow = settings.value
        if (settingsNow.tapToMove) {
            val move = current.tapTarget(now, pile, index)
            if (move != null) {
                selection = null
                play(move)
                return
            }
        }
        selection = if (current.canPickUp(now, pile, index)) Selection(pile, index) else null
    }

    /** A card released over another pile. Returns true when the move was legal. */
    fun onDrop(from: Int, index: Int, to: Int): Boolean {
        if (autoFinishing) return false
        val current = game ?: return false
        val now = state ?: return false
        if (!current.canDrop(now, from, index, to)) return false
        selection = null
        hint = null
        play(Move.cards(from, index, to))
        return true
    }

    private fun play(move: Move) {
        val current = game ?: return
        val before = state ?: return
        var next = current.apply(before, move) ?: return
        undoStack.addLast(before)
        trimHistory()
        redoStack.clear()

        if (settings.value.autoPlaySafe && move.kind == MoveKind.CARDS) {
            next = runSafeAutoPlay(current, next)
        }
        setState(next)
        countDealOnce(current.id)
        evaluate(current, next)
        scheduleSave()
    }

    /** Sends obviously-safe cards home so the player does not have to. */
    private fun runSafeAutoPlay(current: SolitaireGame, from: GameState): GameState {
        var next = from
        repeat(8) {
            val move = when (current) {
                is Klondike -> current.safeAutoPlay(next)
                is FreeCell -> current.safeAutoPlay(next)
                else -> null
            } ?: return next
            next = current.apply(next, move) ?: return next
        }
        return next
    }

    private fun trimHistory() {
        if (settings.value.unlimitedUndo) return
        while (undoStack.size > 3) undoStack.removeFirst()
    }

    fun undo() {
        if (!canUndo) return
        val previous = undoStack.removeLast()
        state?.let { redoStack.addLast(it) }
        setState(previous)
        selection = null
        hint = null
        banner = Banner.NONE
        scheduleSave()
    }

    fun redo() {
        if (!canRedo) return
        val next = redoStack.removeLast()
        state?.let { undoStack.addLast(it) }
        setState(next)
        selection = null
        hint = null
        game?.let { evaluate(it, next) }
        scheduleSave()
    }

    fun showHint() {
        val current = game ?: return
        val now = state ?: return
        selection = null
        hint = current.hint(now)
        viewModelScope.launch {
            delay(2200)
            hint = null
        }
    }

    /**
     * Puts [next] on the board and refreshes the flags that are derived from it.
     * Every path that changes the game goes through here, so the Finish button
     * never has to be recomputed during a recomposition.
     */
    private fun setState(next: GameState?) {
        state = next
        autoFinishAvailable = next != null && game?.let { canAutoFinish(it, next) } == true
    }

    /** True when every remaining card can simply be sent to a foundation. */
    private fun canAutoFinish(current: SolitaireGame, from: GameState): Boolean {
        var probe = from
        if (current.isWon(probe)) return false
        repeat(120) {
            val move = current.autoFinishMove(probe) ?: return false
            probe = current.apply(probe, move) ?: return false
            if (current.isWon(probe)) return true
        }
        return false
    }

    /** Plays the remaining cards home one at a time, so the move is visible. */
    fun autoFinish() {
        val current = game ?: return
        if (autoFinishing) return
        autoFinishing = true
        selection = null
        hint = null
        autoJob = viewModelScope.launch {
            val step = (110 * settings.value.animationSpeed.scale).toLong().coerceAtLeast(16L)
            while (true) {
                val now = state ?: break
                val move = current.autoFinishMove(now) ?: break
                val next = current.apply(now, move) ?: break
                undoStack.addLast(now)
                trimHistory()
                setState(next)
                delay(step)
            }
            autoFinishing = false
            redoStack.clear()
            state?.let { evaluate(current, it) }
            scheduleSave()
        }
    }

    fun dismissBanner() {
        banner = Banner.NONE
    }

    private fun evaluate(current: SolitaireGame, now: GameState) {
        when {
            current.isWon(now) -> {
                banner = Banner.WON
                stopTimer()
                viewModelScope.launch {
                    store.recordWin(current.id, now.score, elapsedSeconds, now.moves)
                    store.clear(current.id)
                }
            }

            current.isStuck(now) -> banner = Banner.STUCK
            else -> banner = Banner.NONE
        }
    }

    private fun countDealOnce(id: GameId) {
        if (dealCounted) return
        dealCounted = true
        viewModelScope.launch { store.recordDeal(id) }
    }

    // ---------------------------------------------------------------- saving

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(900)
            persistNow()
        }
    }

    /** Writes the game in progress out immediately, e.g. when the app pauses. */
    fun persistNow() {
        val current = game ?: return
        val now = state ?: return
        if (current.isWon(now)) return
        val start = dealStart ?: now
        viewModelScope.launch {
            store.save(current.id, now, start, elapsedSeconds, current.variantLabel)
        }
    }

    // ----------------------------------------------------------------- clock

    private fun startTimer() {
        stopTimer()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (banner == Banner.NONE) elapsedSeconds += 1
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun pauseClock() = stopTimer()

    fun resumeClock() {
        if (game != null && banner == Banner.NONE) startTimer()
    }

    // -------------------------------------------------------------- settings

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun resetStats(id: GameId) {
        viewModelScope.launch { store.resetStats(id) }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }
}

fun formatClock(seconds: Long): String {
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes >= 60) {
        "%d:%02d:%02d".format(minutes / 60, minutes % 60, rest)
    } else {
        "%d:%02d".format(minutes, rest)
    }
}
