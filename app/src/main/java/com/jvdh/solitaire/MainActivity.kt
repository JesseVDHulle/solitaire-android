package com.jvdh.solitaire

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jvdh.solitaire.engine.GameId
import com.jvdh.solitaire.ui.GameScreen
import com.jvdh.solitaire.ui.HomeScreen
import com.jvdh.solitaire.ui.SettingsScreen
import com.jvdh.solitaire.ui.StatsScreen
import com.jvdh.solitaire.ui.theme.SolitaireTheme
import com.jvdh.solitaire.vm.SolitaireViewModel

private sealed interface Screen {
    data object Home : Screen
    data class Play(val id: GameId) : Screen
    data object Settings : Screen
    data object Stats : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SolitaireApp() }
    }
}

@Composable
private fun SolitaireApp() {
    val viewModel: SolitaireViewModel = viewModel()
    val settings by viewModel.settings.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val resumable by viewModel.resumable.collectAsState()
    var screen: Screen by remember { mutableStateOf(Screen.Home) }

    SolitaireTheme(settings) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        viewModel.pauseClock()
                        viewModel.persistNow()
                    }

                    Lifecycle.Event.ON_RESUME -> viewModel.resumeClock()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val goHome: () -> Unit = {
            if (screen is Screen.Play) viewModel.close()
            screen = Screen.Home
        }
        BackHandler(enabled = screen != Screen.Home) { goHome() }

        Crossfade(targetState = screen, animationSpec = tween(200), label = "screen") { current ->
            Box(Modifier.fillMaxSize()) {
                when (current) {
                    Screen.Home -> HomeScreen(
                        stats = stats,
                        resumable = resumable,
                        onPlay = { id ->
                            viewModel.open(id)
                            screen = Screen.Play(id)
                        },
                        onSettings = { screen = Screen.Settings },
                        onStats = { screen = Screen.Stats },
                    )

                    is Screen.Play -> PlayScreen(viewModel, goHome)

                    Screen.Settings -> SettingsScreen(
                        settings = settings,
                        onChange = { transform -> viewModel.updateSettings(transform) },
                        onBack = goHome,
                    )

                    Screen.Stats -> StatsScreen(
                        stats = stats,
                        onReset = viewModel::resetStats,
                        onBack = goHome,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayScreen(viewModel: SolitaireViewModel, onBack: () -> Unit) {
    val game = viewModel.game
    val state = viewModel.state
    val settings by viewModel.settings.collectAsState()
    if (game == null || state == null) return

    val view = LocalView.current
    fun buzz() {
        if (settings.haptics) {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    GameScreen(
        game = game,
        state = state,
        settings = settings,
        selection = viewModel.selection,
        hint = viewModel.hint,
        banner = viewModel.banner,
        elapsedSeconds = viewModel.elapsedSeconds,
        canUndo = viewModel.canUndo,
        canRedo = viewModel.canRedo,
        canAutoFinish = viewModel.autoFinishAvailable,
        onTap = { pile, index ->
            buzz()
            viewModel.onTap(pile, index)
        },
        onDrop = { from, index, to ->
            val moved = viewModel.onDrop(from, index, to)
            if (moved) buzz()
            moved
        },
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onHint = viewModel::showHint,
        onAutoFinish = viewModel::autoFinish,
        onNewDeal = viewModel::newDeal,
        onRestartDeal = viewModel::restartDeal,
        onDismissBanner = viewModel::dismissBanner,
        onBack = onBack,
    )
}
