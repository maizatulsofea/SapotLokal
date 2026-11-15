package com.sapotlokal.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.sapotlokal.AppConfig
import com.sapotlokal.data.model.Game
import com.sapotlokal.data.model.GameKind
import com.sapotlokal.data.model.GameType
import com.sapotlokal.data.model.XpEventKey
import com.sapotlokal.data.model.XpSource
import com.sapotlokal.util.MissionDateUtils
import com.sapotlokal.util.ServerClock
import java.time.Instant
import java.time.ZoneId

/**
 * GamesRepository — the ONLY place game plays are read or written (REFRACTOR.md Phase 7).
 *
 * Games pay XP, never shop points. Daily limits are worked out from the live /games rows, never
 * from the phone. One play is ONE atomic write: the /games row, the XP award (event game_{gameID},
 * through XpRepository), and the mission and badge marks the play earns.
 */
object GamesRepository {

    private val db = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference

    fun todayKey(): String =
        Instant.ofEpochMilli(ServerClock.now()).atZone(ZoneId.of(AppConfig.GAMES_RESET_TIME_ZONE)).toLocalDate().toString()

    data class GameStatus(
        val spinsToday: Int,
        val spinAttemptsTotal: Int,
        /** Memory Match wins today that paid XP. */
        val memoryXpWinsToday: Int,
        val memoryAttemptsTotal: Int
    ) {
        val canSpinToday: Boolean get() = spinsToday < AppConfig.SPIN_WHEEL_DAILY_LIMIT
        val memoryXpWinsLeft: Int
            get() = (AppConfig.MEMORY_XP_WINS_PER_DAY - memoryXpWinsToday).coerceAtLeast(0)
    }

    /** One read gives everything the screens need. onResult(null) = could not read (offline / permission). */
    fun fetchStatus(userID: String, onResult: (GameStatus?) -> Unit) {
        db.child("games").orderByChild("userID").equalTo(userID).get()
            .addOnSuccessListener { snap ->
                val today = todayKey()
                var spinsToday = 0; var spinTotal = 0
                var memoryXpWins = 0; var memTotal = 0
                for (child in snap.children) {
                    val g = child.getValue(Game::class.java) ?: continue
                    when (g.gameType) {
                        GameType.SPIN_WHEEL -> {
                            spinTotal++
                            if (g.gameDay == today) spinsToday++
                        }
                        GameType.MEMORY_MATCH -> {
                            memTotal++
                            if (g.gameDay == today && g.gameXpRewarded > 0) memoryXpWins++
                        }
                    }
                }
                onResult(GameStatus(spinsToday, spinTotal, memoryXpWins, memTotal))
            }
            .addOnFailureListener { onResult(null) }
    }

    /** Records today's spin. [xp] is the segment the wheel landed on. onResult(success, xpPaid). */
    fun recordSpin(userID: String, xp: Int, attemptNumber: Int, topSegment: Boolean, onResult: (Boolean, Int) -> Unit) {
        recordPlay(userID, GameType.SPIN_WHEEL, attemptNumber, xp, GameKind.SPIN, topSegment, false, onResult)
    }

    /**
     * Records a Memory Match win. It pays AppConfig.MEMORY_WIN_XP while [xpWinsLeft] > 0; after
     * that the win is still recorded (missions, badges) but pays 0 XP. onResult(success, xpPaid).
     */
    fun recordMemoryWin(userID: String, attemptNumber: Int, xpWinsLeft: Int, perfect: Boolean, onResult: (Boolean, Int) -> Unit) {
        val xp = if (xpWinsLeft > 0) AppConfig.MEMORY_WIN_XP else 0
        recordPlay(userID, GameType.MEMORY_MATCH, attemptNumber, xp, GameKind.MEMORY_WIN, false, perfect, onResult)
    }

    private fun recordPlay(
        userID: String,
        gameType: String,
        attemptNumber: Int,
        xp: Int,
        kind: GameKind,
        topSegment: Boolean,
        perfect: Boolean,
        onResult: (Boolean, Int) -> Unit
    ) {
        if (userID.isBlank()) return onResult(false, 0)
        val gameID = db.child("games").push().key ?: return onResult(false, 0)
        val now = ServerClock.now()
        val today = todayKey()
        val game = Game(
            gameID = gameID,
            userID = userID,
            gameType = gameType,
            gameAttempt = attemptNumber,
            gameXpRewarded = xp,
            gameDay = today,
            gamePlayedAt = now
        )
        BadgeRepository.readContext(userID) { stats, badges ->
            val updates = hashMapOf<String, Any>("games/$gameID" to game)
            updates.putAll(MissionRepository.gamePaths(userID, today, MissionDateUtils.getWeekYearKey(now)))
            updates.putAll(BadgeRepository.gamePaths(userID, kind, now, stats, badges, topSegment, perfect).first)

            if (xp <= 0) {
                db.updateChildren(updates)
                    .addOnSuccessListener { onResult(true, 0) }
                    .addOnFailureListener { onResult(false, 0) }
            } else {
                XpRepository.award(userID, XpEventKey.game(gameID), xp, XpSource.GAME, updates) { award ->
                    onResult(award != null, award?.amount ?: 0)
                }
            }
        }
    }
}
