package com.sapotlokal

import com.sapotlokal.AppConfig.Rank
import com.sapotlokal.data.model.XpEventKey
import com.sapotlokal.util.MissionDateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/** The Phase 2 economy rules from REFRACTOR.md §3: ranks, rounding, week keys and XP event keys. */
class RankEconomyTest {

    @Test
    fun rankBoundaries() {
        assertEquals(Rank.JIRAN_BARU, Rank.fromXp(0))
        assertEquals(Rank.JIRAN_BARU, Rank.fromXp(399))
        assertEquals(Rank.PELANGGAN_SETIA, Rank.fromXp(400))
        assertEquals(Rank.PELANGGAN_SETIA, Rank.fromXp(1_499))
        assertEquals(Rank.KAKI_LOKAL, Rank.fromXp(1_500))
        assertEquals(Rank.OTAI_LOKAL, Rank.fromXp(3_500))
        assertEquals(Rank.OTAI_LOKAL, Rank.fromXp(6_999))
        assertEquals(Rank.LEGENDA_SAPOT, Rank.fromXp(7_000))
        assertEquals(Rank.LEGENDA_SAPOT, Rank.fromXp(1_000_000))
    }

    @Test
    fun rankLadderIsOrderedAndBonusesMatchThePlan() {
        assertEquals(listOf(1, 2, 3, 4, 5), Rank.entries.map { it.level })
        assertEquals(listOf(1.0, 1.25, 1.5, 2.0, 3.0), Rank.entries.map { it.pointsBonus })
        assertTrue(Rank.entries.zipWithNext().all { (a, b) -> a.minXp < b.minXp })
        assertEquals(Rank.PELANGGAN_SETIA, Rank.JIRAN_BARU.next)
        assertNull(Rank.LEGENDA_SAPOT.next)
        assertEquals(Rank.KAKI_LOKAL, Rank.fromLevel(3))
        assertEquals(Rank.JIRAN_BARU, Rank.fromLevel(99))
    }

    @Test
    fun shopPointsRoundHalvesUp() {
        assertEquals(10, AppConfig.shopPointsForVisit(10, Rank.JIRAN_BARU))
        assertEquals(13, AppConfig.shopPointsForVisit(10, Rank.PELANGGAN_SETIA)) // 12.5
        assertEquals(6, AppConfig.shopPointsForVisit(5, Rank.PELANGGAN_SETIA))   // 6.25
        assertEquals(30, AppConfig.shopPointsForVisit(20, Rank.KAKI_LOKAL))
        assertEquals(30, AppConfig.shopPointsForVisit(10, Rank.LEGENDA_SAPOT))
        assertEquals(150, AppConfig.shopPointsForVisit(50, Rank.LEGENDA_SAPOT))
    }

    @Test
    fun rankBonusLabelsDropTrailingZeros() {
        val labels = Rank.entries.map { com.sapotlokal.util.RankText.bonus(it.pointsBonus) }
        assertEquals(listOf("1", "1.25", "1.5", "2", "3"), labels)
    }

    @Test
    fun everyAllowedRateStaysWithinTheRulesCap() {
        // Rules allow pointEarnedAmount ≤ pointsPerVisit × 3; the top bonus is exactly 3x.
        for (rate in AppConfig.ALLOWED_POINTS_PER_VISIT) for (rank in Rank.entries) {
            assertTrue(AppConfig.shopPointsForVisit(rate, rank) <= rate * 3)
        }
        assertTrue(AppConfig.DEFAULT_POINTS_PER_VISIT in AppConfig.ALLOWED_POINTS_PER_VISIT)
    }

    @Test
    fun xpValuesFitTheirEventCaps() {
        assertTrue(AppConfig.VISIT_XP <= AppConfig.XP_EVENT_MAX.getValue("visit_"))
        assertTrue(AppConfig.SPIN_XP_SEGMENTS.max() <= AppConfig.XP_EVENT_MAX.getValue("game_"))
        assertTrue(AppConfig.MEMORY_WIN_XP <= AppConfig.XP_EVENT_MAX.getValue("game_"))
        assertTrue(AppConfig.LEADERBOARD_XP.max() <= AppConfig.XP_EVENT_MAX.getValue("leaderboard_"))
        assertTrue(AppConfig.XP_EVENT_MAX.values.all { it <= AppConfig.XP_MAX_PER_WRITE })
    }

    @Test
    fun xpEventKeysUseTheRulePrefixes() {
        val keys = listOf(
            XpEventKey.visit("p1"), XpEventKey.game("g1"), XpEventKey.weeklyMission("w_visits", "2026-W46"),
            XpEventKey.lifetimeMission("l_shops"), XpEventKey.badge("hantu_malam"), XpEventKey.leaderboard("2026-W46")
        )
        keys.forEach { key -> assertTrue(key, AppConfig.XP_EVENT_MAX.keys.any { key.startsWith(it) }) }
        assertEquals("wmission_w_visits_2026-W46", XpEventKey.weeklyMission("w_visits", "2026-W46"))
    }

    @Test
    fun weekRollsOverAtMondayMidnightMalaysiaTime() {
        fun kl(s: String) = ZonedDateTime.parse(s).toInstant().toEpochMilli()
        // Sunday 23:59 and Monday 00:00 in Kuala Lumpur sit in different weeks.
        val sunday = kl("2026-11-15T23:59:00+08:00[Asia/Kuala_Lumpur]")
        val monday = kl("2026-11-16T00:00:00+08:00[Asia/Kuala_Lumpur]")
        assertEquals("2026-W46", MissionDateUtils.getWeekYearKey(sunday))
        assertEquals("2026-W47", MissionDateUtils.getWeekYearKey(monday))
        assertEquals(monday, MissionDateUtils.getWeekStartMillis(monday + 3_600_000))
        // Year boundary: 1 Jan 2027 is a Friday in ISO week 53 of 2026.
        assertEquals("2026-W53", MissionDateUtils.getWeekYearKey(kl("2027-01-01T12:00:00+08:00[Asia/Kuala_Lumpur]")))
    }
}
