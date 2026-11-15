package com.sapotlokal.data.model

import com.sapotlokal.R
import com.sapotlokal.util.MissionDateUtils
import java.time.Instant
import java.time.ZonedDateTime

/**
 * The eight badges (REFRACTOR.md §3.5). Names are the same in both languages; [howToRes] is the
 * one line the badge sheet shows. A badge is unlocked by the write that earns it and pays [xp]
 * once, when the customer claims it (BadgeRepository).
 *
 * Badges never reward the same thing as a mission.
 */
enum class BadgeDef(
    val id: String,
    val title: String,
    val howToRes: Int,
    val xp: Int,
    val iconRes: Int
) {
    LANGKAH_KANAN("langkah_kanan", "Langkah Kanan", R.string.badge_how_langkah_kanan, 25, R.drawable.ic_badge_first_scan),
    REGULAR("regular", "Regular", R.string.badge_how_regular, 50, R.drawable.ic_badge_repeat_visit),
    HANTU_MALAM("hantu_malam", "Hantu Malam", R.string.badge_how_hantu_malam, 50, R.drawable.ic_badge_night_owl),
    COMBO("combo", "Combo", R.string.badge_how_combo, 50, R.drawable.ic_badge_combo),
    NASIB_BAIK("nasib_baik", "Nasib Baik", R.string.badge_how_nasib_baik, 50, R.drawable.ic_badge_wheel_max),
    OTAK_CERDAS("otak_cerdas", "Otak Cerdas", R.string.badge_how_otak_cerdas, 50, R.drawable.ic_badge_memory_perfect),
    BELANJA_DIRI("belanja_diri", "Belanja Diri", R.string.badge_how_belanja_diri, 50, R.drawable.ic_badge_treat),
    JUARA_MINGGU("juara_minggu", "Juara Minggu", R.string.badge_how_juara_minggu, 100, R.drawable.ic_badge_podium);

    companion object {
        fun fromId(id: String): BadgeDef? = entries.find { it.id == id }

        /** Visit on this many different days at one shop in one week for [REGULAR]. */
        const val REGULAR_DAYS = 3

        /** A visit at or after this hour (Malaysia time) earns [HANTU_MALAM]. */
        const val NIGHT_HOUR = 20
    }
}

/** What a game play was, for the badges it can earn. [mark] is its key under userStats/{uid}/days/{day}. */
enum class GameKind(val mark: String) { SPIN("spin"), MEMORY_WIN("memory") }

/**
 * Which badges a visit or a game play unlocks. Pure, so the rules are unit-tested; BadgeRepository
 * reads the database into these arguments and turns the result into paths.
 *
 * [todayMarks] holds what the customer already did today ("visit", "spin", "memory"), [owned] the
 * ids of badges already unlocked. Days and hours are Malaysia time.
 */
object BadgeRules {

    private const val VISIT_MARK = "visit"

    fun dayKey(now: Long): String =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), MissionDateUtils.KL_ZONE).toLocalDate().toString()

    fun hour(now: Long): Int =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), MissionDateUtils.KL_ZONE).hour

    /** [totalVisits] counts visits before this one; [shopDaysThisWeek] the days already visited at this shop this week. */
    fun onVisit(
        now: Long, totalVisits: Long, shopDaysThisWeek: Set<String>, todayMarks: Set<String>, owned: Set<String>
    ): List<BadgeDef> {
        val earned = mutableListOf<BadgeDef>()
        if (totalVisits == 0L) earned += BadgeDef.LANGKAH_KANAN
        if ((shopDaysThisWeek + dayKey(now)).size >= BadgeDef.REGULAR_DAYS) earned += BadgeDef.REGULAR
        if (hour(now) >= BadgeDef.NIGHT_HOUR) earned += BadgeDef.HANTU_MALAM
        if (GameKind.SPIN.mark in todayMarks && GameKind.MEMORY_WIN.mark in todayMarks) earned += BadgeDef.COMBO
        return earned.filter { it.id !in owned }
    }

    /** [topSegment]: the wheel landed on its biggest prize. [perfect]: a Memory Match win without a wrong pair. */
    fun onGame(
        kind: GameKind, todayMarks: Set<String>, owned: Set<String>, topSegment: Boolean, perfect: Boolean
    ): List<BadgeDef> {
        val earned = mutableListOf<BadgeDef>()
        if (kind == GameKind.SPIN && topSegment) earned += BadgeDef.NASIB_BAIK
        if (kind == GameKind.MEMORY_WIN && perfect) earned += BadgeDef.OTAK_CERDAS
        val other = if (kind == GameKind.SPIN) GameKind.MEMORY_WIN else GameKind.SPIN
        if (VISIT_MARK in todayMarks && other.mark in todayMarks) earned += BadgeDef.COMBO
        return earned.filter { it.id !in owned }
    }
}

/** One badge as the customer sees it. */
data class BadgeState(val def: BadgeDef, val unlockedAt: Long, val claimedAt: Long) {
    val unlocked: Boolean get() = unlockedAt > 0L
    val claimed: Boolean get() = claimedAt > 0L
    val claimable: Boolean get() = unlocked && !claimed
}
