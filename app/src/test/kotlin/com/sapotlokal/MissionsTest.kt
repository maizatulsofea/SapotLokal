package com.sapotlokal

import com.sapotlokal.data.model.MissionDef
import com.sapotlokal.data.model.MissionInputs
import com.sapotlokal.data.model.MissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The missions in REFRACTOR.md §3.4 and how their state is worked out. */
class MissionsTest {

    private val week = "2026-W46"

    @Test
    fun missionsMatchThePlan() {
        assertEquals(listOf("w_visits", "w_explore", "w_play"), MissionDef.WEEKLY.map { it.id })
        assertEquals(listOf("l_visits", "l_shops", "l_weeks"), MissionDef.LIFETIME.map { it.id })
        assertEquals(listOf(3, 2, 3, 50, 10, 4), MissionDef.entries.map { it.target })
        assertEquals(listOf(40, 30, 30, 300, 300, 250), MissionDef.entries.map { it.xp })
    }

    @Test
    fun missionXpFitsTheRulesCaps() {
        MissionDef.WEEKLY.forEach { assertTrue(it.xp <= AppConfig.XP_EVENT_MAX.getValue("wmission_")) }
        MissionDef.LIFETIME.forEach { assertTrue(it.xp <= AppConfig.XP_EVENT_MAX.getValue("lmission_")) }
    }

    @Test
    fun weeklyKeysCarryTheWeekAndLifetimeKeysDont() {
        assertEquals("w_visits_2026-W46", MissionDef.W_VISITS.progressKey(week))
        assertEquals("wmission_w_visits_2026-W46", MissionDef.W_VISITS.eventKey(week))
        assertEquals("l_shops", MissionDef.L_SHOPS.progressKey(week))
        assertEquals("lmission_l_shops", MissionDef.L_SHOPS.eventKey(week))
        // A new week gives fresh keys, so last week's claims don't carry over.
        assertTrue(MissionDef.W_PLAY.progressKey("2026-W47") != MissionDef.W_PLAY.progressKey(week))
    }

    @Test
    fun progressComesFromTheRightCounter() {
        val inputs = MissionInputs(
            visitsThisWeek = 2, shopsThisWeek = 1, playDaysThisWeek = 3,
            totalVisits = 51, shopsEver = 4, weeksAllMissions = 0
        )
        val byId = MissionState.all(inputs, week).associateBy { it.def }
        assertEquals(2, byId.getValue(MissionDef.W_VISITS).progress)
        assertEquals(1, byId.getValue(MissionDef.W_EXPLORE).progress)
        assertTrue(byId.getValue(MissionDef.W_PLAY).claimable)
        assertTrue(byId.getValue(MissionDef.L_VISITS).claimable)
        assertEquals(50, byId.getValue(MissionDef.L_VISITS).progressShown) // capped at the target
        assertFalse(byId.getValue(MissionDef.L_SHOPS).done)
    }

    @Test
    fun aClaimedMissionIsNotClaimableAgain() {
        val inputs = MissionInputs(visitsThisWeek = 5, claimed = setOf("w_visits_$week"))
        val visits = MissionState.all(inputs, week).first { it.def == MissionDef.W_VISITS }
        assertTrue(visits.done)
        assertTrue(visits.claimed)
        assertFalse(visits.claimable)
    }
}
