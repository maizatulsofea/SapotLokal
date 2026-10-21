package com.sapotlokal.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.sapotlokal.AppConfig
import com.sapotlokal.data.model.Game
import com.sapotlokal.data.model.GameType
import com.sapotlokal.data.model.PointSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * GamesRepository — the ONLY place game plays are read or written.
 * Daily limits are computed from the live database, never from data stored on the phone (Rule 43).
 * Every play that earns points writes the /games row AND the /points row in ONE atomic update (Rule 9).
 */
object GamesRepository {

    private val db = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference

    fun todayKey(): String =
        LocalDate.now(ZoneId.of(AppConfig.GAMES_RESET_TIME_ZONE)).toString()

    data class GameStatus(
        val spinsToday: Int,
        val spinAttemptsTotal: Int,
        val memoryPointsToday: Int,
        val memoryAttemptsTotal: Int
    ) {
        val canSpinToday: Boolean get() = spinsToday < AppConfig.SPIN_WHEEL_DAILY_LIMIT
        val memoryRoomToday: Int
            get() = (AppConfig.MEMORY_MATCH_DAILY_CAP_POINTS - memoryPointsToday).coerceAtLeast(0)
    }

    /** One read gives everything the UI needs. onResult(null) = could not read (offline / permission). */
    fun fetchStatus(userID: String, onResult: (GameStatus?) -> Unit) {
        // Mirror the exact query pattern PointRepository.fetchAllPointsForUser() uses.
        db.child("games").orderByChild("userID").equalTo(userID).get()
            .addOnSuccessListener { snap ->
                val today = todayKey()
                var spinsToday = 0; var spinTotal = 0
                var memPointsToday = 0; var memTotal = 0
                for (child in snap.children) {
                    val g = child.getValue(Game::class.java) ?: continue
                    when (g.gameType) {
                        GameType.SPIN_WHEEL -> {
                            spinTotal++
                            if (g.gameDay == today) spinsToday++
                        }
                        GameType.MEMORY_MATCH -> {
                            memTotal++
                            if (g.gameDay == today) memPointsToday += g.gamePointsRewarded
                        }
                    }
                }
                onResult(GameStatus(spinsToday, spinTotal, memPointsToday, memTotal))
            }
            .addOnFailureListener { onResult(null) }
    }

    fun recordSpin(userID: String, pointsWon: Int, attemptNumber: Int, onResult: (Boolean) -> Unit) =
        recordPlay(userID, GameType.SPIN_WHEEL, PointSource.SPIN_WHEEL, pointsWon, attemptNumber, onResult)

    fun recordMemoryWin(userID: String, pointsAwarded: Int, attemptNumber: Int, onResult: (Boolean) -> Unit) =
        recordPlay(userID, GameType.MEMORY_MATCH, PointSource.MEMORY_MATCH, pointsAwarded, attemptNumber, onResult)

    private fun recordPlay(
        userID: String, gameType: String, pointSource: String,
        points: Int, attemptNumber: Int, onResult: (Boolean) -> Unit
    ) {
        val gameID = db.child("games").push().key ?: return onResult(false)
        val game = Game(
            gameID = gameID, userID = userID, gameType = gameType,
            gameAttempt = attemptNumber, gamePointsRewarded = points,
            gameDay = todayKey(), gamePlayedAt = System.currentTimeMillis()
        )
        val updates = hashMapOf<String, Any?>("games/$gameID" to game)
        if (points > 0) {
            val point = PointRepository.newGamePointEntry(userID, pointSource, points)
                ?: return onResult(false)
            updates["points/${point.pointID}"] = point
        }
        db.updateChildren(updates)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}
