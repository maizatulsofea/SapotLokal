package com.sapotlokal.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.data.model.MissionDef
import com.sapotlokal.data.model.MissionInputs
import com.sapotlokal.data.model.MissionState
import com.sapotlokal.data.model.XpSource
import com.sapotlokal.util.MissionDateUtils
import com.sapotlokal.util.ServerClock

/**
 * MissionRepository — mission progress and claims (REFRACTOR.md §3.4, Phase 5).
 *
 * Progress is never a separate "sync" pass: the scan and game writes already keep the counters
 * missions read ([visitPaths], [gamePaths] ride in those writes). This repository only reads them
 * and claims rewards through [XpRepository.award], so a mission can be paid once at most.
 */
object MissionRepository {

    private val db = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference

    // ── Progress paths (added to the scan and game writes) ────────────────────

    /** A visit at [vendorID] this week counts toward "Try 2 different shops". */
    fun visitPaths(uid: String, vendorID: String, weekKey: String): Map<String, Any> =
        mapOf("missionProgress/$uid/${MissionDef.W_EXPLORE.progressKey(weekKey)}/shops/$vendorID" to true)

    /** A game played on [dayKey] ("yyyy-MM-dd") this week counts toward "Play on 3 days". */
    fun gamePaths(uid: String, dayKey: String, weekKey: String): Map<String, Any> =
        mapOf("missionProgress/$uid/${MissionDef.W_PLAY.progressKey(weekKey)}/days/$dayKey" to true)

    // ── Reading ───────────────────────────────────────────────────────────────

    /** Live missions for [uid] in the current week. Stop with [Listening.stop]. */
    class Listening internal constructor(private val refs: List<Pair<DatabaseReference, ValueEventListener>>) {
        fun stop() = refs.forEach { (ref, l) -> ref.removeEventListener(l) }
    }

    fun listen(uid: String, onUpdate: (List<MissionState>) -> Unit): Listening {
        val week = MissionDateUtils.getWeekYearKey()
        var stats: DataSnapshot? = null
        var weekly: DataSnapshot? = null
        var progress: DataSnapshot? = null

        fun emit() {
            val s = stats ?: return
            val w = weekly ?: return
            val p = progress ?: return
            onUpdate(MissionState.all(inputsFrom(s, w, p, week), week))
        }

        fun watch(ref: DatabaseReference, set: (DataSnapshot) -> Unit): Pair<DatabaseReference, ValueEventListener> {
            val l = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) { set(snapshot); emit() }
                override fun onCancelled(error: DatabaseError) {}
            }
            ref.addValueEventListener(l)
            return ref to l
        }

        if (uid.isBlank()) return Listening(emptyList())
        return Listening(listOf(
            watch(db.child("userStats").child(uid)) { stats = it },
            watch(db.child("weeklyVisits").child(week).child(uid)) { weekly = it },
            watch(db.child("missionProgress").child(uid)) { progress = it }
        ))
    }

    internal fun inputsFrom(stats: DataSnapshot, weekly: DataSnapshot, progress: DataSnapshot, week: String) = MissionInputs(
        visitsThisWeek = weekly.child("visits").getValue(Long::class.java)?.toInt() ?: 0,
        shopsThisWeek = progress.child(MissionDef.W_EXPLORE.progressKey(week)).child("shops").childrenCount.toInt(),
        playDaysThisWeek = progress.child(MissionDef.W_PLAY.progressKey(week)).child("days").childrenCount.toInt(),
        totalVisits = stats.child("totalVisits").getValue(Long::class.java)?.toInt() ?: 0,
        shopsEver = stats.child("shopsVisited").childrenCount.toInt(),
        weeksAllMissions = stats.child("weeksAllMissions").getValue(Long::class.java)?.toInt() ?: 0,
        claimed = progress.children
            .filter { (it.child("claimedAt").getValue(Long::class.java) ?: 0L) > 0L }
            .mapNotNull { it.key }
            .toSet()
    )

    // ── Claiming ──────────────────────────────────────────────────────────────

    /**
     * Pays [mission]'s XP and marks it claimed in one write. When this is the last of the three
     * weekly missions this week, the same write counts the week toward "Finish every weekly
     * mission 4 times". [all] is the current list, used to know what is already claimed.
     */
    fun claim(uid: String, mission: MissionState, all: List<MissionState>, onResult: (XpRepository.Award?) -> Unit) {
        if (!mission.claimable) return onResult(null)
        val def = mission.def
        val now = ServerClock.now()
        val key = def.progressKey(mission.weekKey)
        val extra = hashMapOf<String, Any>(
            "missionProgress/$uid/$key/claimedAt" to now,
            "missionProgress/$uid/$key/progress" to mission.progressShown
        )
        if (def.weekly) {
            val othersClaimed = all.filter { it.def.weekly && it.def != def }.all { it.claimed }
            if (othersClaimed) extra["userStats/$uid/weeksAllMissions"] = ServerValue.increment(1)
        }
        XpRepository.award(
            uid = uid,
            eventKey = def.eventKey(mission.weekKey),
            amount = def.xp,
            source = if (def.weekly) XpSource.WEEKLY_MISSION else XpSource.LIFETIME_MISSION,
            extraUpdates = extra,
            onResult = onResult
        )
    }
}
