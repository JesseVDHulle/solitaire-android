package com.jvdh.solitaire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvdh.solitaire.data.GameStats
import com.jvdh.solitaire.engine.GameId
import com.jvdh.solitaire.engine.blurbFor
import com.jvdh.solitaire.ui.theme.LocalTableColors

@Composable
fun HomeScreen(
    stats: Map<GameId, GameStats>,
    resumable: Set<GameId>,
    onPlay: (GameId) -> Unit,
    onSettings: () -> Unit,
    onStats: () -> Unit,
) {
    val table = LocalTableColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(table.top, table.bottom))),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 28.dp, 20.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Solitaire",
                            color = table.onTable,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Five ways to pass the time",
                            color = table.onTable.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    RoundIconButton(onStats) {
                        Icon(Icons.Filled.BarChart, "Statistics", tint = table.onTable)
                    }
                    Spacer(Modifier.width(8.dp))
                    RoundIconButton(onSettings) {
                        Icon(Icons.Filled.Settings, "Settings", tint = table.onTable)
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            items(GameId.entries.toList(), key = { it.name }) { id ->
                GameCard(
                    id = id,
                    stats = stats[id] ?: GameStats(),
                    resumable = id in resumable,
                    onClick = { onPlay(id) },
                )
            }
        }
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun GameCard(
    id: GameId,
    stats: GameStats,
    resumable: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            GameGlyph(id)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(id.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = blurbFor(id),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (stats.played == 0) {
                        "Not played yet"
                    } else {
                        "${stats.won} of ${stats.played} won · ${stats.winRate}%"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
            if (resumable) {
                TextButton(onClick = onClick) { Text("Resume") }
            }
        }
    }
}

/** A tiny fan of cards, drawn with the player's own card back. */
@Composable
private fun GameGlyph(id: GameId) {
    val shape = RoundedCornerShape(6.dp)
    val accent = LocalTableColors.current.accent
    Box(
        modifier = Modifier
            .size(54.dp, 62.dp),
        contentAlignment = Alignment.Center,
    ) {
        val offsets = when (id) {
            GameId.KLONDIKE -> listOf(-10f to 2f, 0f to -2f, 10f to 2f)
            GameId.SPIDER -> listOf(-12f to 0f, -4f to 0f, 4f to 0f, 12f to 0f)
            GameId.FREECELL -> listOf(-9f to -6f, 9f to -6f, 0f to 6f)
            GameId.TRIPEAKS -> listOf(-11f to 5f, 0f to -6f, 11f to 5f)
            GameId.PYRAMID -> listOf(0f to -8f, -8f to 4f, 8f to 4f)
        }
        offsets.forEachIndexed { index, (dx, dy) ->
            Box(
                modifier = Modifier
                    .offset(x = dx.dp, y = dy.dp)
                    .size(24.dp, 34.dp)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accent.copy(alpha = 0.85f - index * 0.08f),
                                accent.copy(alpha = 0.55f - index * 0.06f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "♠♥♣♦"[index % 4].toString(),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}
