package com.sapotlokal.data.model

/**
 * XP, counters and progress records (REFRACTOR.md §4).
 *
 * Booleans are avoided on purpose: Firebase maps a Kotlin `isX` property to "x", which has bitten
 * this codebase before. "Done" and "claimed" are timestamps (0 = not yet).
 */

/** Stored at /xpEvents/{uid}/{eventKey}. The key is deterministic, so each event can only be written once. */
data class XpEvent(
    val amount: Int = 0,
    val source: String = "",
    val timestamp: Long = 0L
)

object XpSource {
    const val VISIT = "visit"
    const val GAME = "game"
    const val WEEKLY_MISSION = "weekly_mission"
    const val LIFETIME_MISSION = "lifetime_mission"
    const val BADGE = "badge"
    const val LEADERBOARD = "leaderboard"
}

/** Builds the deterministic /xpEvents keys. Prefixes must match AppConfig.XP_EVENT_MAX and the rules. */
object XpEventKey {
    fun visit(pointID: String) = "visit_$pointID"
    fun game(gameID: String) = "game_$gameID"
    fun weeklyMission(missionId: String, weekKey: String) = "wmission_${missionId}_$weekKey"
    fun lifetimeMission(missionId: String) = "lmission_$missionId"
    fun badge(badgeId: String) = "badge_$badgeId"
    fun leaderboard(weekKey: String) = "leaderboard_$weekKey"
}

/** Stored at /userStats/{uid}. Counters that missions and badges read; updated in the same write as the event. */
data class UserStats(
    val totalVisits: Int = 0,
    val shopsVisited: Map<String, Boolean> = emptyMap(),
    val weeksAllMissions: Int = 0,
    val lastVisitAt: Long = 0L
)

/** Stored at /weeklyVisits/{weekKey}/{uid}. The leaderboard reads only this node. */
data class WeeklyVisit(
    val visits: Int = 0,
    val lastVisitAt: Long = 0L,
    val userName: String = "",
    val avatar: String = "",
    val rank: Int = 1
)

/** Stored at /missionProgress/{uid}/{progressKey}; progressKey = {missionId}_{weekKey} or {missionId}. */
data class MissionProgress(
    val progress: Int = 0,
    val completedAt: Long = 0L,
    val claimedAt: Long = 0L
)

/** Stored at /badges/{uid}/{badgeId}. */
data class BadgeProgress(
    val unlockedAt: Long = 0L,
    val claimedAt: Long = 0L
)
