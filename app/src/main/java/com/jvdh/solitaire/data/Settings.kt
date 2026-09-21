package com.jvdh.solitaire.data

import com.jvdh.solitaire.engine.GameOptions

enum class ThemeMode(val label: String) {
    SYSTEM("Follow system"),
    LIGHT("Light"),
    DARK("Dark"),
    BLACK("Pure black"),
}

/** The colour of the table the cards sit on. */
enum class TableTheme(val label: String) {
    FELT("Classic felt"),
    MIDNIGHT("Midnight"),
    SLATE("Slate"),
    MAHOGANY("Mahogany"),
    OCEAN("Ocean"),
    PLUM("Plum"),
    SAND("Sand"),
}

enum class CardBackStyle(val label: String) {
    WEAVE("Weave"),
    DIAMONDS("Diamonds"),
    DOTS("Dots"),
    WAVES("Waves"),
    GRID("Grid"),
    SOLID("Solid"),
}

enum class CardBackColor(val label: String) {
    RED("Red"),
    BLUE("Blue"),
    GREEN("Green"),
    PURPLE("Purple"),
    CHARCOAL("Charcoal"),
    AMBER("Amber"),
}

enum class FaceStyle(val label: String) {
    CLASSIC("Classic"),
    BOLD("Big index"),
    MINIMAL("Minimal"),
}

enum class AnimationSpeed(val label: String, val scale: Float) {
    OFF("Off", 0f),
    FAST("Fast", 0.6f),
    NORMAL("Normal", 1f),
    RELAXED("Relaxed", 1.6f),
}

/** How much of a face-down card peeks out above the next one. */
enum class CardSpacing(val label: String, val faceUp: Float, val faceDown: Float) {
    TIGHT("Tight", 0.20f, 0.08f),
    NORMAL("Normal", 0.26f, 0.11f),
    ROOMY("Roomy", 0.32f, 0.14f),
}

/** Everything the player can change, in one immutable bundle. */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val tableTheme: TableTheme = TableTheme.FELT,
    val cardBackStyle: CardBackStyle = CardBackStyle.WEAVE,
    val cardBackColor: CardBackColor = CardBackColor.BLUE,
    val faceStyle: FaceStyle = FaceStyle.CLASSIC,
    val fourColourDeck: Boolean = false,
    val leftHanded: Boolean = false,
    val tapToMove: Boolean = true,
    val autoPlaySafe: Boolean = false,
    val autoFinishPrompt: Boolean = true,
    val animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    val cardSpacing: CardSpacing = CardSpacing.NORMAL,
    val cardScale: Float = 1f,
    val haptics: Boolean = true,
    val showTimer: Boolean = true,
    val showScore: Boolean = true,
    val confirmRestart: Boolean = true,
    val unlimitedUndo: Boolean = true,
    val gameOptions: GameOptions = GameOptions(),
)
