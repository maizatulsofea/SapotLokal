package com.sapotlokal.data.model

import com.sapotlokal.R

/**
 * The six missions (REFRACTOR.md §3.4): three that reset every Monday 00:00 Malaysia time and three
 * that can each be claimed once ever. Progress is never stored as a single number that could drift:
 * it is read from counters the scan and game writes already keep (see [MissionInputs]).
 */
enum class MissionDef(
    val id: String,
    val weekly: Boolean,
    val target: Int,
    val xp: Int,
    val nameRes: Int,
    val iconRes: Int,
    /** Where tapping an unfinished mission takes the customer. */
    val startsAt: Start
) {
    W_VISITS("w_visits", true, 3, 40, R.string.mission_w_visits, R.drawable.ic_store, Start.SCAN),
    W_EXPLORE("w_explore", true, 2, 30, R.string.mission_w_explore, R.drawable.ic_compass, Start.SHOPS),
    W_PLAY("w_play", true, 3, 30, R.string.mission_w_play, R.drawable.ic_wheel, Start.GAMES),
    L_VISITS("l_visits", false, 50, 300, R.string.mission_l_visits, R.drawable.ic_store, Start.SCAN),
    L_SHOPS("l_shops", false, 10, 300, R.string.mission_l_shops, R.drawable.ic_map_pin, Start.SHOPS),
    L_WEEKS("l_weeks", false, 4, 250, R.string.mission_l_weeks, R.drawable.ic_star, Start.MISSIONS);

    enum class Start { SCAN, SHOPS, GAMES, MISSIONS }

    /** Key under /missionProgress/{uid}: weekly missions get the week, lifetime ones don't. */
    fun progressKey(weekKey: String): String = if (weekly) "${id}_$weekKey" else id

    /** The XP event key for claiming it (mirrors the rules' prefixes). */
    fun eventKey(weekKey: String): String =
        if (weekly) XpEventKey.weeklyMission(id, weekKey) else XpEventKey.lifetimeMission(id)

    /** Progress toward [target], read from the customer's counters. */
    fun progress(inputs: MissionInputs): Int = when (this) {
        W_VISITS -> inputs.visitsThisWeek
        W_EXPLORE -> inputs.shopsThisWeek
        W_PLAY -> inputs.playDaysThisWeek
        L_VISITS -> inputs.totalVisits
        L_SHOPS -> inputs.shopsEver
        L_WEEKS -> inputs.weeksAllMissions
    }

    companion object {
        val WEEKLY = entries.filter { it.weekly }
        val LIFETIME = entries.filter { !it.weekly }
    }
}

/** Everything mission progress is read from, for one customer in one week. */
data class MissionInputs(
    val visitsThisWeek: Int = 0,      // /weeklyVisits/{week}/{uid}/visits
    val shopsThisWeek: Int = 0,       // /missionProgress/{uid}/w_explore_{week}/shops (count)
    val playDaysThisWeek: Int = 0,    // /missionProgress/{uid}/w_play_{week}/days (count)
    val totalVisits: Int = 0,         // /userStats/{uid}/totalVisits
    val shopsEver: Int = 0,           // /userStats/{uid}/shopsVisited (count)
    val weeksAllMissions: Int = 0,    // /userStats/{uid}/weeksAllMissions
    /** Progress keys already claimed. */
    val claimed: Set<String> = emptySet()
)

/** One mission as the customer sees it this week. */
data class MissionState(
    val def: MissionDef,
    val weekKey: String,
    val progress: Int,
    val claimed: Boolean
) {
    val done: Boolean get() = progress >= def.target
    val claimable: Boolean get() = done && !claimed
    val progressShown: Int get() = progress.coerceAtMost(def.target)

    companion object {
        fun all(inputs: MissionInputs, weekKey: String): List<MissionState> = MissionDef.entries.map { def ->
            MissionState(def, weekKey, def.progress(inputs), def.progressKey(weekKey) in inputs.claimed)
        }
    }
}
