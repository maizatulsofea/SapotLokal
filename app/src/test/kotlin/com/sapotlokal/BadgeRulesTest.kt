package com.sapotlokal

import com.sapotlokal.data.model.BadgeDef
import com.sapotlokal.data.model.BadgeRules
import com.sapotlokal.data.model.GameKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** When each badge unlocks (REFRACTOR.md §3.5). */
class BadgeRulesTest {

    private val kl = ZoneId.of("Asia/Kuala_Lumpur")

    /** Wednesday 12 Nov 2025 at [hour]:[minute] Malaysia time. */
    private fun at(hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2025, 11, 12, hour, minute, 0, 0, kl).toInstant().toEpochMilli()

    private fun visit(
        now: Long = at(12), totalVisits: Long = 5, shopDays: Set<String> = emptySet(),
        today: Set<String> = emptySet(), owned: Set<String> = emptySet()
    ) = BadgeRules.onVisit(now, totalVisits, shopDays, today, owned)

    @Test
    fun firstEverVisitUnlocksLangkahKanan() {
        assertEquals(listOf(BadgeDef.LANGKAH_KANAN), visit(totalVisits = 0))
        assertEquals(emptyList<BadgeDef>(), visit(totalVisits = 1))
    }

    @Test
    fun regularNeedsThreeDifferentDaysAtOneShopThisWeek() {
        assertEquals(emptyList<BadgeDef>(), visit(shopDays = setOf("2025-11-10")))
        assertEquals(listOf(BadgeDef.REGULAR), visit(shopDays = setOf("2025-11-10", "2025-11-11")))
        // A second visit on a day already counted is not a new day.
        assertEquals(emptyList<BadgeDef>(), visit(shopDays = setOf("2025-11-10", "2025-11-12")))
    }

    @Test
    fun hantuMalamStartsAtEightPmMalaysiaTime() {
        assertEquals(emptyList<BadgeDef>(), visit(now = at(19, 59)))
        assertEquals(listOf(BadgeDef.HANTU_MALAM), visit(now = at(20, 0)))
        assertEquals(listOf(BadgeDef.HANTU_MALAM), visit(now = at(23, 59)))
        assertEquals(emptyList<BadgeDef>(), visit(now = at(0, 30)))
    }

    @Test
    fun comboNeedsAVisitASpinAndAMemoryWinOnTheSameDay() {
        assertEquals(emptyList<BadgeDef>(), visit(today = setOf("spin")))
        assertEquals(listOf(BadgeDef.COMBO), visit(today = setOf("spin", "memory")))

        assertEquals(emptyList<BadgeDef>(), BadgeRules.onGame(GameKind.SPIN, setOf("visit"), emptySet(), false, false))
        assertEquals(listOf(BadgeDef.COMBO), BadgeRules.onGame(GameKind.SPIN, setOf("visit", "memory"), emptySet(), false, false))
        assertEquals(listOf(BadgeDef.COMBO), BadgeRules.onGame(GameKind.MEMORY_WIN, setOf("visit", "spin"), emptySet(), false, false))
    }

    @Test
    fun gameBadgesNeedTheTopSegmentOrAPerfectWin() {
        assertEquals(listOf(BadgeDef.NASIB_BAIK), BadgeRules.onGame(GameKind.SPIN, emptySet(), emptySet(), topSegment = true, perfect = false))
        assertEquals(emptyList<BadgeDef>(), BadgeRules.onGame(GameKind.SPIN, emptySet(), emptySet(), topSegment = false, perfect = true))
        assertEquals(listOf(BadgeDef.OTAK_CERDAS), BadgeRules.onGame(GameKind.MEMORY_WIN, emptySet(), emptySet(), topSegment = false, perfect = true))
        assertEquals(emptyList<BadgeDef>(), BadgeRules.onGame(GameKind.MEMORY_WIN, emptySet(), emptySet(), topSegment = true, perfect = false))
    }

    @Test
    fun aBadgeAlreadyUnlockedIsNotUnlockedAgain() {
        val everything = visit(now = at(21), totalVisits = 0, shopDays = setOf("2025-11-10", "2025-11-11"), today = setOf("spin", "memory"))
        assertEquals(listOf(BadgeDef.LANGKAH_KANAN, BadgeDef.REGULAR, BadgeDef.HANTU_MALAM, BadgeDef.COMBO), everything)

        val owned = everything.map { it.id }.toSet()
        assertTrue(visit(now = at(21), totalVisits = 0, shopDays = setOf("2025-11-10", "2025-11-11"), today = setOf("spin", "memory"), owned = owned).isEmpty())
        assertTrue(BadgeRules.onGame(GameKind.SPIN, setOf("visit", "memory"), setOf("combo", "nasib_baik"), true, false).isEmpty())
    }

    @Test
    fun theDayRollsOverAtMidnightMalaysiaTime() {
        assertEquals("2025-11-12", BadgeRules.dayKey(at(0, 0)))
        assertEquals("2025-11-12", BadgeRules.dayKey(at(23, 59)))
        // 23:59 UTC on the 11th is already the 12th in Malaysia.
        assertEquals("2025-11-12", BadgeRules.dayKey(ZonedDateTime.of(2025, 11, 11, 23, 59, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()))
    }
}
