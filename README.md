# Solitaire

Five solitaires for Android in one app: **Klondike**, **Spider**, **FreeCell**,
**TriPeaks** and **Pyramid**. Jetpack Compose, Material 3, no ads, no network
permission, nothing to sign in to.

## The games

| Game | Rules | Options |
| --- | --- | --- |
| Klondike | Seven columns, four foundations, a stock you turn over. | Draw 1 or 3, redeal limit, standard / Vegas / no scoring |
| Spider | Ten columns; same-suit runs travel together, finished runs leave the table. | 1, 2 or 4 suits |
| FreeCell | Everything face up, four holding cells, no luck left to it. | 2, 3 or 4 cells |
| TriPeaks | Clear three peaks with one long chain of adjacent ranks. | Aces wrap to King, or not |
| Pyramid | Pair cards that add up to thirteen; Kings go alone. | 0, 1 or 2 redeals |

## Controls

Both schemes work at once, so nothing has to be learned before playing.

- **Tap** a card and it goes wherever it most likely belongs: home to a
  foundation first, then to a column that already has cards. Tapping the stock
  draws, deals, or recycles, depending on the game.
- **Drag** a card, or a properly built run, and drop it where you want it. The
  target pile lights up while a legal drop is under the card, and the card
  glides into place from wherever you let go.
- **Tap, then tap again** to pick the destination yourself. This is how Pyramid
  pairs are made, and it is the fallback in every game when a tap has no
  obvious destination. Tapping the same card again cancels.
- Hit-testing walks the cards from front to back, so a tap on the thin exposed
  sliver of a buried card picks *that* card, not the one lying over it.

Under the board: **Undo**, **Redo**, **Hint**, **Finish** (which appears only
when every remaining card really can be sent home), **Restart** the same deal,
and **New deal**.

## What you can change

Settings has a live card preview at the top, and covers:

- **Appearance** — theme (system / light / dark / pure black), seven table
  colours, six card-back designs in six colours, three card-face styles, and a
  four-colour deck.
- **Board** — card size, column spacing, animation speed (including off), and a
  left-handed layout that mirrors the whole table.
- **Playing** — tap-to-move on or off, auto-play of cards that can no longer be
  useful, unlimited or three-move undo, confirmation before abandoning a deal,
  vibration, and whether the timer and score are shown.
- **Rules** — the per-game options in the table above. They take effect on the
  next deal of that game.

Games in progress are saved per game, along with the deal they started from, so
Restart still works after a cold start. Wins, streaks, best score, fastest time
and fewest moves are kept per game on the Statistics screen.

## Building

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew test                 # the JVM unit tests
```

Needs JDK 17 and an Android SDK with API 35 (`sdk.dir` in `local.properties`, or
`ANDROID_HOME` set). Minimum supported device is API 24.

## How it is put together

```
engine/     the rules; plain Kotlin, no Android imports at all
  games/    one file per solitaire
board/      turns a game plus a viewport into positioned cards; also plain Kotlin
ui/         Compose: card drawing, the board and its gestures, the screens
data/       settings, saved games and statistics, on DataStore
vm/         the game in progress, undo history and the clock
```

Every game state is immutable, so undo is a stack of old states rather than a
set of inverse moves, and a saved game is one encoded state. Every game exposes
the same `Move` shape and the same layout description, so the renderer and the
gesture handling are written once rather than five times.

`engine/` and `board/` carry no Android dependency, which is what makes them
testable on the JVM: **62 unit tests** cover the deals, the legal and illegal
moves in each game, card conservation across long play-outs, save round trips,
and the board layout — that every game fits its viewport, that long columns are
squeezed rather than run off the bottom, and that hit-testing picks the right
card.
