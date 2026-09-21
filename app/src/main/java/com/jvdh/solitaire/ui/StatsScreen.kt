package com.jvdh.solitaire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvdh.solitaire.data.GameStats
import com.jvdh.solitaire.engine.GameId
import com.jvdh.solitaire.vm.formatClock

@Composable
fun StatsScreen(
    stats: Map<GameId, GameStats>,
    onReset: (GameId) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader("Statistics", onBack) }
        items(GameId.entries.toList(), key = { it.name }) { id ->
            StatsCard(id, stats[id] ?: GameStats(), onReset = { onReset(id) })
        }
    }
}

@Composable
private fun StatsCard(id: GameId, stats: GameStats, onReset: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(id.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    text = "${stats.winRate}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { stats.winRate / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Figure("Played", stats.played.toString())
                Figure("Won", stats.won.toString())
                Figure("Streak", stats.currentStreak.toString())
                Figure("Best streak", stats.bestStreak.toString())
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Figure("Best score", if (stats.won == 0) "—" else stats.bestScore.toString())
                Figure(
                    label = "Fastest",
                    value = if (stats.bestTimeSeconds == 0L) "—" else formatClock(stats.bestTimeSeconds),
                )
                Figure("Fewest moves", if (stats.fewestMoves == 0) "—" else stats.fewestMoves.toString())
            }
            if (stats.played > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onReset) { Text("Reset") }
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
