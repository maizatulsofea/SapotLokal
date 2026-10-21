package com.sapotlokal.ui.games

/**
 * Presentation-only constants for the games (animation timing, layout, symbols).
 * NEVER put points, limits, or anything that changes the economy here — that lives in AppConfig (Rule 8).
 */
object GameConfig {
    const val SPIN_DURATION_MS = 4000L
    const val SPIN_EXTRA_ROTATIONS = 5

    val MEMORY_EMOJIS = listOf("🍰", "☕", "🍜", "🥐", "🍦", "🧋")
    const val MEMORY_COLUMNS = 4
    const val MEMORY_MISMATCH_DELAY_MS = 800L
    const val MEMORY_MATCH_REVEAL_DELAY_MS = 250L
    const val MEMORY_FLIP_HALF_MS = 120L
}

