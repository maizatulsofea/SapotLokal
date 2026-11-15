package com.sapotlokal.util

import java.util.Locale

/** Small text helpers for ranks and rank bonuses. */
object RankText {
    /** A rank bonus without trailing zeros: 1.25 → "1.25", 2.0 → "2", 1.5 → "1.5". */
    fun bonus(value: Double): String =
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}
