package com.sapotlokal

import com.sapotlokal.data.model.WeeklyVisit
import com.sapotlokal.data.repository.LeaderboardRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The weekly visit race (REFRACTOR.md §3.6). */
class LeaderboardTest {

    private fun v(visits: Int, at: Long) = WeeklyVisit(visits = visits, lastVisitAt = at)

    @Test
    fun mostVisitsFirstAndTiesGoToWhoeverGotThereFirst() {
        val ranked = LeaderboardRepository.rank(
            mapOf("a" to v(2, 300), "b" to v(5, 900), "c" to v(2, 100), "d" to v(3, 500))
        )
        assertEquals(listOf("b", "d", "c", "a"), ranked.map { it.uid })
        assertEquals(listOf(1, 2, 3, 4), ranked.map { it.place })
    }

    @Test
    fun youNeedAVisitToAppear() {
        val ranked = LeaderboardRepository.rank(mapOf("a" to v(0, 0), "b" to v(1, 10)))
        assertEquals(listOf("b"), ranked.map { it.uid })
    }

    @Test
    fun prizesMatchThePlan() {
        assertEquals(listOf(150, 100, 75), AppConfig.LEADERBOARD_XP)
        assertEquals(100, LeaderboardRepository.Prize("2026-W45", 2).xp)
        assertTrue(AppConfig.LEADERBOARD_XP.all { it <= AppConfig.XP_EVENT_MAX.getValue("leaderboard_") })
    }

    @Test
    fun onlyTheLastFourFinishedWeeksCanBeClaimed() {
        // Sunday 15 Nov 2026, 20:00 in Kuala Lumpur is in week 46.
        val now = java.time.ZonedDateTime.parse("2026-11-15T20:00:00+08:00[Asia/Kuala_Lumpur]").toInstant().toEpochMilli()
        assertEquals(listOf("2026-W45", "2026-W44", "2026-W43", "2026-W42"), LeaderboardRepository.claimableWeeks(now))
    }
}
