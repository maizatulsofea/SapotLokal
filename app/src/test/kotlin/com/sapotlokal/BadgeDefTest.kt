package com.sapotlokal

import com.sapotlokal.data.model.BadgeDef
import com.sapotlokal.data.model.BadgeState
import com.sapotlokal.data.model.MissionDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The badges in REFRACTOR.md §3.5. */
class BadgeDefTest {

    @Test
    fun eightBadgesWithThePlannedIdsAndXp() {
        assertEquals(
            listOf("langkah_kanan", "regular", "hantu_malam", "combo", "nasib_baik", "otak_cerdas", "belanja_diri", "juara_minggu"),
            BadgeDef.entries.map { it.id }
        )
        assertEquals(listOf(25, 50, 50, 50, 50, 50, 50, 100), BadgeDef.entries.map { it.xp })
        assertEquals(425, BadgeDef.entries.sumOf { it.xp })
    }

    @Test
    fun badgeXpFitsTheRulesCap() {
        BadgeDef.entries.forEach { assertTrue(it.xp <= AppConfig.XP_EVENT_MAX.getValue("badge_")) }
    }

    @Test
    fun badgeIdsMatchTheRulesWhitelist() {
        // docs/firebase-rules.json only accepts these ids under /badges/{uid}.
        val rulesIds = "langkah_kanan|regular|hantu_malam|combo|nasib_baik|otak_cerdas|belanja_diri|juara_minggu".split("|")
        assertEquals(rulesIds.toSet(), BadgeDef.entries.map { it.id }.toSet())
    }

    @Test
    fun noBadgeSharesAnIdWithAMission() {
        val missionIds = MissionDef.entries.map { it.id }.toSet()
        assertTrue(BadgeDef.entries.none { it.id in missionIds })
    }

    @Test
    fun stateMovesFromLockedToReadyToClaimed() {
        val locked = BadgeState(BadgeDef.HANTU_MALAM, 0L, 0L)
        assertFalse(locked.unlocked); assertFalse(locked.claimable)
        val ready = locked.copy(unlockedAt = 1L)
        assertTrue(ready.claimable)
        val claimed = ready.copy(claimedAt = 2L)
        assertTrue(claimed.unlocked); assertFalse(claimed.claimable)
    }

    @Test
    fun fromIdFindsEveryBadge() {
        BadgeDef.entries.forEach { assertEquals(it, BadgeDef.fromId(it.id)) }
        assertEquals(null, BadgeDef.fromId("tapah_ranger"))
    }
}
