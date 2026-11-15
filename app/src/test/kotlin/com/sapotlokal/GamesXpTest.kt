package com.sapotlokal

import com.sapotlokal.data.repository.GamesRepository.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Games pay XP (REFRACTOR.md §3.1, Phase 7). */
class GamesXpTest {

    @Test
    fun wheelSegmentsAreTheXpValuesFromThePlan() {
        assertEquals(listOf(2, 3, 4, 5, 6, 8, 10, 15), AppConfig.SPIN_XP_SEGMENTS)
        assertEquals(15, AppConfig.SPIN_XP_SEGMENTS.max())
    }

    @Test
    fun aDayOfGamesStaysAroundTwentyFiveXp() {
        val bestDay = AppConfig.SPIN_XP_SEGMENTS.max() + AppConfig.MEMORY_WIN_XP * AppConfig.MEMORY_XP_WINS_PER_DAY
        assertEquals(25, bestDay)
        assertTrue(bestDay < AppConfig.VISIT_XP * 2) // two visits beat a perfect day of games
    }

    @Test
    fun spinIsOncePerDay() {
        assertTrue(GameStatus(spinsToday = 0, spinAttemptsTotal = 4, memoryXpWinsToday = 0, memoryAttemptsTotal = 0).canSpinToday)
        assertFalse(GameStatus(spinsToday = 1, spinAttemptsTotal = 5, memoryXpWinsToday = 0, memoryAttemptsTotal = 0).canSpinToday)
    }

    @Test
    fun onlyTheFirstTwoMemoryWinsADayPayXp() {
        assertEquals(2, GameStatus(0, 0, memoryXpWinsToday = 0, memoryAttemptsTotal = 0).memoryXpWinsLeft)
        assertEquals(1, GameStatus(0, 0, memoryXpWinsToday = 1, memoryAttemptsTotal = 1).memoryXpWinsLeft)
        assertEquals(0, GameStatus(0, 0, memoryXpWinsToday = 2, memoryAttemptsTotal = 2).memoryXpWinsLeft)
        assertEquals(0, GameStatus(0, 0, memoryXpWinsToday = 5, memoryAttemptsTotal = 5).memoryXpWinsLeft)
    }
}
