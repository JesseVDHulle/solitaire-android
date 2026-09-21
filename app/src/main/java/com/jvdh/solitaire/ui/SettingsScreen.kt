package com.jvdh.solitaire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvdh.solitaire.data.AnimationSpeed
import com.jvdh.solitaire.data.CardBackColor
import com.jvdh.solitaire.data.CardBackStyle
import com.jvdh.solitaire.data.CardSpacing
import com.jvdh.solitaire.data.FaceStyle
import com.jvdh.solitaire.data.Settings
import com.jvdh.solitaire.data.TableTheme
import com.jvdh.solitaire.data.ThemeMode
import com.jvdh.solitaire.engine.Card
import com.jvdh.solitaire.engine.Suit
import com.jvdh.solitaire.engine.games.ScoreMode
import com.jvdh.solitaire.ui.theme.LocalTableColors
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: ((Settings) -> Settings) -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item { ScreenHeader("Settings", onBack) }
        item { CardPreview(settings) }

        item { SectionHeader("Appearance") }
        item {
            ChoiceRow(
                title = "Theme",
                options = ThemeMode.entries.map { it.label to it },
                selected = settings.themeMode,
                onSelect = { value -> onChange { it.copy(themeMode = value) } },
            )
        }
        item {
            ChoiceRow(
                title = "Table",
                options = TableTheme.entries.map { it.label to it },
                selected = settings.tableTheme,
                onSelect = { value -> onChange { it.copy(tableTheme = value) } },
            )
        }
        item {
            ChoiceRow(
                title = "Card back",
                options = CardBackStyle.entries.map { it.label to it },
                selected = settings.cardBackStyle,
                onSelect = { value -> onChange { it.copy(cardBackStyle = value) } },
            )
        }
        item {
            ChoiceRow(
                title = "Card back colour",
                options = CardBackColor.entries.map { it.label to it },
                selected = settings.cardBackColor,
                onSelect = { value -> onChange { it.copy(cardBackColor = value) } },
            )
        }
        item {
            ChoiceRow(
                title = "Card face",
                options = FaceStyle.entries.map { it.label to it },
                selected = settings.faceStyle,
                onSelect = { value -> onChange { it.copy(faceStyle = value) } },
            )
        }
        item {
            SwitchRow(
                title = "Four-colour deck",
                subtitle = "Clubs green, diamonds blue — easier to tell apart",
                checked = settings.fourColourDeck,
                onChange = { value -> onChange { it.copy(fourColourDeck = value) } },
            )
        }

        item { SectionHeader("Board") }
        item {
            SliderRow(
                title = "Card size",
                value = settings.cardScale,
                range = 0.75f..1f,
                format = { "${(it * 100).roundToInt()}%" },
                onChange = { value -> onChange { it.copy(cardScale = value) } },
            )
        }
        item {
            ChoiceRow(
                title = "Column spacing",
                options = CardSpacing.entries.map { it.label to it },
                selected = settings.cardSpacing,
                onSelect = { value -> onChange { it.copy(cardSpacing = value) } },
            )
        }
        item {
            ChoiceRow(
                title = "Animation",
                options = AnimationSpeed.entries.map { it.label to it },
                selected = settings.animationSpeed,
                onSelect = { value -> onChange { it.copy(animationSpeed = value) } },
            )
        }
        item {
            SwitchRow(
                title = "Left-handed layout",
                subtitle = "Mirrors the table so the stock sits under your thumb",
                checked = settings.leftHanded,
                onChange = { value -> onChange { it.copy(leftHanded = value) } },
            )
        }

        item { SectionHeader("Playing") }
        item {
            SwitchRow(
                title = "Tap to move",
                subtitle = "A tap sends a card to the best place; drag still works",
                checked = settings.tapToMove,
                onChange = { value -> onChange { it.copy(tapToMove = value) } },
            )
        }
        item {
            SwitchRow(
                title = "Auto-play safe cards",
                subtitle = "Sends cards home when they can no longer be useful",
                checked = settings.autoPlaySafe,
                onChange = { value -> onChange { it.copy(autoPlaySafe = value) } },
            )
        }
        item {
            SwitchRow(
                title = "Unlimited undo",
                subtitle = "Off limits you to the last three moves",
                checked = settings.unlimitedUndo,
                onChange = { value -> onChange { it.copy(unlimitedUndo = value) } },
            )
        }
        item {
            SwitchRow(
                title = "Confirm new deal",
                subtitle = "Ask before abandoning a game in progress",
                checked = settings.confirmRestart,
                onChange = { value -> onChange { it.copy(confirmRestart = value) } },
            )
        }
        item {
            SwitchRow(
                title = "Vibrate on move",
                checked = settings.haptics,
                onChange = { value -> onChange { it.copy(haptics = value) } },
            )
        }
        item {
            SwitchRow(
                title = "Show timer",
                checked = settings.showTimer,
                onChange = { value -> onChange { it.copy(showTimer = value) } },
            )
        }
        item {
            SwitchRow(
                title = "Show score",
                checked = settings.showScore,
                onChange = { value -> onChange { it.copy(showScore = value) } },
            )
        }

        item { SectionHeader("Klondike") }
        item {
            ChoiceRow(
                title = "Cards per draw",
                options = listOf("Draw 1" to 1, "Draw 3" to 3),
                selected = settings.gameOptions.klondikeDraw,
                onSelect = { value ->
                    onChange { it.copy(gameOptions = it.gameOptions.copy(klondikeDraw = value)) }
                },
            )
        }
        item {
            ChoiceRow(
                title = "Redeals",
                options = listOf("Unlimited" to -1, "None" to 0, "One" to 1, "Three" to 3),
                selected = settings.gameOptions.klondikeRedeals,
                onSelect = { value ->
                    onChange { it.copy(gameOptions = it.gameOptions.copy(klondikeRedeals = value)) }
                },
            )
        }
        item {
            ChoiceRow(
                title = "Scoring",
                options = listOf(
                    "Standard" to ScoreMode.STANDARD,
                    "Vegas" to ScoreMode.VEGAS,
                    "None" to ScoreMode.NONE,
                ),
                selected = settings.gameOptions.klondikeScoring,
                onSelect = { value ->
                    onChange { it.copy(gameOptions = it.gameOptions.copy(klondikeScoring = value)) }
                },
            )
        }

        item { SectionHeader("Spider") }
        item {
            ChoiceRow(
                title = "Suits",
                subtitle = "One suit is a gentle game; four is a long evening",
                options = listOf("1 suit" to 1, "2 suits" to 2, "4 suits" to 4),
                selected = settings.gameOptions.spiderSuits,
                onSelect = { value ->
                    onChange { it.copy(gameOptions = it.gameOptions.copy(spiderSuits = value)) }
                },
            )
        }

        item { SectionHeader("FreeCell") }
        item {
            ChoiceRow(
                title = "Free cells",
                subtitle = "Fewer cells, fewer cards you can shuffle at once",
                options = listOf("2" to 2, "3" to 3, "4" to 4),
                selected = settings.gameOptions.freeCellCells,
                onSelect = { value ->
                    onChange { it.copy(gameOptions = it.gameOptions.copy(freeCellCells = value)) }
                },
            )
        }

        item { SectionHeader("TriPeaks") }
        item {
            SwitchRow(
                title = "Aces wrap round",
                subtitle = "An Ace joins both the King and the two",
                checked = settings.gameOptions.triPeaksWrap,
                onChange = { value ->
                    onChange { it.copy(gameOptions = it.gameOptions.copy(triPeaksWrap = value)) }
                },
            )
        }

        item { SectionHeader("Pyramid") }
        item {
            ChoiceRow(
                title = "Redeals",
                options = listOf("None" to 0, "One" to 1, "Two" to 2),
                selected = settings.gameOptions.pyramidRedeals,
                onSelect = { value ->
                    onChange { it.copy(gameOptions = it.gameOptions.copy(pyramidRedeals = value)) }
                },
            )
        }
        item {
            Text(
                text = "Rule changes apply to the next deal of that game.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp, 18.dp),
            )
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp),
        )
    }
}

/** A row of cards drawn with the settings as they stand. */
@Composable
private fun CardPreview(settings: Settings) {
    val table = LocalTableColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(table.top, table.bottom)))
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CardView(Card(0, Suit.SPADES, 1), true, 58.dp, settings, accent = table.accent)
                CardView(Card(1, Suit.HEARTS, 12), true, 58.dp, settings, accent = table.accent)
                CardView(Card(2, Suit.DIAMONDS, 10), true, 58.dp, settings, accent = table.accent)
                CardView(Card(3, Suit.CLUBS, 7), false, 58.dp, settings, accent = table.accent)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in options) {
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(format(value), style = MaterialTheme.typography.titleMedium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = 4,
        )
    }
}
