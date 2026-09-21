package com.jvdh.solitaire.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvdh.solitaire.data.Settings
import com.jvdh.solitaire.engine.GameState
import com.jvdh.solitaire.engine.SolitaireGame
import com.jvdh.solitaire.ui.theme.LocalTableColors
import com.jvdh.solitaire.vm.Banner
import com.jvdh.solitaire.vm.formatClock

/** Everything around the board: the clock, the controls and the end-of-game banner. */
@Composable
fun GameScreen(
    game: SolitaireGame,
    state: GameState,
    settings: Settings,
    selection: Selection?,
    hint: com.jvdh.solitaire.engine.Move?,
    banner: Banner,
    elapsedSeconds: Long,
    canUndo: Boolean,
    canRedo: Boolean,
    canAutoFinish: Boolean,
    onTap: (Int, Int) -> Unit,
    onDrop: (Int, Int, Int) -> Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onHint: () -> Unit,
    onAutoFinish: () -> Unit,
    onNewDeal: () -> Unit,
    onRestartDeal: () -> Unit,
    onDismissBanner: () -> Unit,
    onBack: () -> Unit,
) {
    val table = LocalTableColors.current
    var confirmNew by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(table.bottom),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(table.top)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            IconButton(onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = table.onTable)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = game.id.title,
                    color = table.onTable,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = game.statusText(state),
                    color = table.onTable.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (settings.showScore) {
                Readout("Score", state.score.toString(), table.onTable)
                Spacer(Modifier.width(10.dp))
            }
            if (settings.showTimer) {
                Readout("Time", formatClock(elapsedSeconds), table.onTable)
                Spacer(Modifier.width(10.dp))
            }
        }

        Box(Modifier.weight(1f)) {
            GameBoard(
                game = game,
                state = state,
                settings = settings,
                selection = selection,
                hint = hint,
                onTap = onTap,
                onDrop = onDrop,
                modifier = Modifier.fillMaxSize(),
            )

            AnimatedVisibility(
                visible = banner != Banner.NONE,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
                modifier = Modifier.align(Alignment.Center),
            ) {
                EndBanner(
                    banner = banner,
                    score = state.score,
                    moves = state.moves,
                    seconds = elapsedSeconds,
                    onNewDeal = onNewDeal,
                    onDismiss = onDismissBanner,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(table.top)
                .navigationBarsPadding()
                .padding(vertical = 4.dp),
        ) {
            ControlButton(Icons.AutoMirrored.Filled.Undo, "Undo", canUndo, onUndo)
            ControlButton(Icons.AutoMirrored.Filled.Redo, "Redo", canRedo, onRedo)
            ControlButton(Icons.Filled.Lightbulb, "Hint", true, onHint)
            ControlButton(
                icon = Icons.Filled.AutoAwesome,
                label = "Finish",
                enabled = canAutoFinish,
                onClick = onAutoFinish,
            )
            ControlButton(Icons.Filled.Replay, "Restart", true) {
                if (settings.confirmRestart && state.moves > 0) confirmRestart = true else onRestartDeal()
            }
            ControlButton(Icons.Filled.Refresh, "New deal", true) {
                if (settings.confirmRestart && state.moves > 0) confirmNew = true else onNewDeal()
            }
        }
    }

    if (confirmNew) {
        ConfirmDialog(
            title = "Deal a new game?",
            body = "This game will count as a loss in your statistics.",
            confirmLabel = "New deal",
            onConfirm = { confirmNew = false; onNewDeal() },
            onDismiss = { confirmNew = false },
        )
    }
    if (confirmRestart) {
        ConfirmDialog(
            title = "Start this deal again?",
            body = "The same cards, back where they started.",
            confirmLabel = "Restart",
            onConfirm = { confirmRestart = false; onRestartDeal() },
            onDismiss = { confirmRestart = false },
        )
    }
}

@Composable
private fun Readout(label: String, value: String, colour: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label.uppercase(),
            color = colour.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = colour,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val table = LocalTableColors.current
    val tint = if (enabled) table.onTable else table.onTable.copy(alpha = 0.3f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(60.dp),
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, label, tint = tint)
        }
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun EndBanner(
    banner: Banner,
    score: Int,
    moves: Int,
    seconds: Long,
    onNewDeal: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = Modifier.padding(24.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
        ) {
            Text(
                text = if (banner == Banner.WON) "You did it" else "No moves left",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (banner == Banner.WON) {
                    "$score points · $moves moves · ${formatClock(seconds)}"
                } else {
                    "Undo a move, or deal again."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onDismiss) { Text("Keep looking") }
                Button(onNewDeal) { Text("New deal") }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { Button(onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
