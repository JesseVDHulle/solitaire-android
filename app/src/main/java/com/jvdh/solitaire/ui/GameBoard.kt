package com.jvdh.solitaire.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.jvdh.solitaire.board.BoardRect
import com.jvdh.solitaire.board.CardPlacement
import com.jvdh.solitaire.board.computeGeometry
import com.jvdh.solitaire.data.Settings
import com.jvdh.solitaire.engine.Card
import com.jvdh.solitaire.engine.GameState
import com.jvdh.solitaire.engine.Move
import com.jvdh.solitaire.engine.MoveKind
import com.jvdh.solitaire.engine.PileKind
import com.jvdh.solitaire.engine.SolitaireGame
import com.jvdh.solitaire.ui.theme.LocalTableColors

/** Which card the player has tapped and is waiting to pair, if any. */
data class Selection(val pile: Int, val index: Int)

/** A stack of cards currently following the finger. */
private data class Drag(
    val pile: Int,
    val index: Int,
    val cards: List<Card>,
    val grab: Offset,
    val position: Offset,
)

/**
 * The table. One pointer handler covers the whole surface and hit-tests against
 * the laid-out cards, which keeps drag, drop and tap consistent across all five
 * games instead of each card fighting for the gesture.
 */
@Composable
fun GameBoard(
    game: SolitaireGame,
    state: GameState,
    settings: Settings,
    selection: Selection?,
    hint: Move?,
    onTap: (pile: Int, index: Int) -> Unit,
    onDrop: (from: Int, index: Int, to: Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val table = LocalTableColors.current
    BoxWithConstraints(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(table.top, table.bottom)),
        ),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val geometry = remember(
            game, state, widthPx, heightPx,
            settings.cardSpacing, settings.cardScale, settings.leftHanded,
        ) {
            computeGeometry(
                game = game,
                state = state,
                containerWidth = widthPx,
                containerHeight = heightPx,
                faceUpStep = settings.cardSpacing.faceUp,
                faceDownStep = settings.cardSpacing.faceDown,
                scale = settings.cardScale,
                leftHanded = settings.leftHanded,
            )
        }
        if (geometry.cardWidth <= 0f) return@BoxWithConstraints

        val cardWidth = with(density) { geometry.cardWidth.toDp() }
        var drag by remember { mutableStateOf<Drag?>(null) }
        // Where each card was let go, so it glides into its pile instead of
        // snapping there the moment the finger lifts.
        val releasedAt = remember(game) { HashMap<Int, Offset>() }

        // The gesture lambdas outlive a single composition, so read the latest
        // state through these rather than capturing it.
        val currentGeometry by rememberUpdatedState(geometry)
        val currentState by rememberUpdatedState(state)
        val currentGame by rememberUpdatedState(game)
        val currentOnTap by rememberUpdatedState(onTap)
        val currentOnDrop by rememberUpdatedState(onDrop)
        val currentSpacing by rememberUpdatedState(settings.cardSpacing)

        val dragTargets = drag?.let { active ->
            currentState.piles.indices.filter {
                game.canDrop(currentState, active.pile, active.index, it)
            }
        }.orEmpty()

        fun tap(point: Offset) {
            val geo = currentGeometry
            val card = geo.cardAt(point.x, point.y)
            if (card != null) {
                currentOnTap(card.pile, card.index)
            } else {
                geo.pileAt(point.x, point.y)?.let { currentOnTap(it, -1) }
            }
        }

        fun grab(point: Offset): Drag? {
            val geo = currentGeometry
            val hit = geo.cardAt(point.x, point.y) ?: return null
            if (!currentGame.canPickUp(currentState, hit.pile, hit.index)) return null
            val pile = currentState[hit.pile]
            return Drag(
                pile = hit.pile,
                index = hit.index,
                cards = pile.cards.subList(hit.index, pile.size).toList(),
                grab = Offset(point.x - hit.x, point.y - hit.y),
                position = Offset(hit.x, hit.y),
            )
        }

        fun release(active: Drag) {
            val geo = currentGeometry
            val step = geo.cardHeight * currentSpacing.faceUp
            active.cards.forEachIndexed { offset, card ->
                releasedAt[card.code] = active.position + Offset(0f, offset * step)
            }
            val rect = BoardRect(
                active.position.x,
                active.position.y,
                active.position.x + geo.cardWidth,
                active.position.y + geo.cardHeight,
            )
            geo.dropCandidates(rect).firstOrNull {
                currentOnDrop(active.pile, active.index, it)
            }
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                // One handler for both gestures. Two competing detectors would
                // race over who consumes the move that crosses the touch slop,
                // and a drag would sometimes register as a tap as well.
                .pointerInput(game) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val origin = down.position
                        val slop = viewConfiguration.touchSlop
                        var travelled = Offset.Zero
                        var active: Drag? = null

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (active != null) release(active) else tap(origin)
                                break
                            }
                            travelled += change.positionChange()
                            if (active == null && travelled.getDistance() > slop) {
                                active = grab(origin)
                                // Nothing liftable here, and the finger has
                                // already moved, so this is not a tap either.
                                if (active == null) break
                            }
                            if (active != null) {
                                change.consume()
                                active = active.copy(position = change.position - active.grab)
                                drag = active
                            }
                        }
                        drag = null
                    }
                },
        ) {
            for ((pile, rect) in geometry.slotRects) {
                val kind = state[pile].kind
                val empty = state[pile].isEmpty
                val highlighted = dragTargets.contains(pile)
                if (!empty && !highlighted) continue
                // A cleared peak or pyramid slot leaves a gap, not an outline.
                if (kind == PileKind.RESERVE && !highlighted) continue
                key("slot-$pile") {
                    SlotOutline(
                        rect = if (empty) rect else geometry.landingRect(pile),
                        width = cardWidth,
                        highlighted = highlighted,
                        glyph = if (empty) slotGlyph(game, state, pile, kind) else null,
                        stroke = if (highlighted) table.dropTarget else table.slot,
                    )
                }
            }

            // Composed in a stable order and stacked with zIndex, so a card that
            // changes pile animates instead of being torn down and rebuilt.
            val dragged = drag
            for (placement in geometry.placements.sortedBy { it.card.code }) {
                if (dragged != null &&
                    placement.pile == dragged.pile &&
                    placement.index >= dragged.index
                ) {
                    continue
                }
                key(placement.card.code) {
                    AnimatedCard(
                        placement = placement,
                        from = releasedAt.remove(placement.card.code),
                        width = cardWidth,
                        settings = settings,
                        selected = selection?.pile == placement.pile &&
                            selection.index == placement.index,
                        hinted = hint.highlights(placement),
                        accent = table.accent,
                    )
                }
            }

            dragged?.let { active ->
                val step = geometry.cardHeight * settings.cardSpacing.faceUp
                active.cards.forEachIndexed { offset, card ->
                    key("drag-${card.code}") {
                        CardView(
                            card = card,
                            faceUp = true,
                            width = cardWidth,
                            settings = settings,
                            accent = table.accent,
                            modifier = Modifier
                                .zIndex(1000f + offset)
                                .graphicsLayer {
                                    translationX = active.position.x
                                    translationY = active.position.y + offset * step
                                    scaleX = 1.04f
                                    scaleY = 1.04f
                                    shadowElevation = 14f
                                    shape = RoundedCornerShape(cardCorner(cardWidth))
                                },
                        )
                    }
                }
            }
        }
    }
}

private fun Move?.highlights(placement: CardPlacement): Boolean {
    if (this == null || kind != MoveKind.CARDS) return false
    return from == placement.pile && index == placement.index
}

/** A hint at what an empty pile is for. */
private fun slotGlyph(
    game: SolitaireGame,
    state: GameState,
    pile: Int,
    kind: PileKind,
): String? = when (kind) {
    PileKind.STOCK -> if (game.canDraw(state)) "↻" else "—"
    PileKind.FOUNDATION -> "✦"
    PileKind.FREE_CELL -> "○"
    else -> null
}

@Composable
private fun SlotOutline(
    rect: BoardRect,
    width: Dp,
    highlighted: Boolean,
    glyph: String?,
    stroke: Color,
) {
    EmptySlot(
        width = width,
        stroke = stroke,
        glyph = glyph,
        fill = if (highlighted) stroke.copy(alpha = 0.16f) else Color.Transparent,
        modifier = Modifier
            .zIndex(if (highlighted) 900f else 0f)
            .graphicsLayer {
                translationX = rect.left
                translationY = rect.top
            },
    )
}

@Composable
private fun AnimatedCard(
    placement: CardPlacement,
    from: Offset?,
    width: Dp,
    settings: Settings,
    selected: Boolean,
    hinted: Boolean,
    accent: Color,
) {
    val corners = RoundedCornerShape(cardCorner(width))
    val target = Offset(placement.x, placement.y)
    val position = remember { Animatable(from ?: target, Offset.VectorConverter) }
    val duration = (240 * settings.animationSpeed.scale).toInt()
    LaunchedEffect(target, duration) {
        if (duration <= 0) {
            position.snapTo(target)
        } else {
            position.animateTo(target, tween(duration, easing = FastOutSlowInEasing))
        }
    }
    CardView(
        card = placement.card,
        faceUp = placement.faceUp,
        width = width,
        settings = settings,
        selected = selected,
        hinted = hinted,
        accent = accent,
        modifier = Modifier
            .zIndex(placement.z.toFloat())
            .graphicsLayer {
                translationX = position.value.x
                translationY = position.value.y
                shadowElevation = if (selected) 10f else 2f
                shape = corners
            },
    )
}
