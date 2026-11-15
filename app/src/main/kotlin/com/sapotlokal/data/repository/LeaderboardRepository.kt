package com.sapotlokal.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.data.model.BadgeDef
import com.sapotlokal.data.model.WeeklyVisit
import com.sapotlokal.data.model.XpEventKey
import com.sapotlokal.data.model.XpSource
import com.sapotlokal.util.MissionDateUtils
import com.sapotlokal.util.ServerClock

/**
 * LeaderboardRepository — the weekly visit race (REFRACTOR.md §3.6, Phase 8).
 *
 * It reads ONLY /weeklyVisits/{weekKey}, which every valid scan keeps (+1 visit). Order: most
 * visits first; on a tie, whoever reached that count first. You need at least one visit to appear.
 * After a week ends its top 3 can claim AppConfig.LEADERBOARD_XP for up to
 * AppConfig.LEADERBOARD_CLAIM_WEEKS weeks; claiming also unlocks the Juara Minggu badge.
 */
object LeaderboardRepository {

    private val db by lazy { FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference }

    data class Entry(
        val uid: String,
        val userName: String,
        val avatar: String,
        val rank: Int,
        val visits: Int,
        val lastVisitAt: Long,
        val place: Int
    )

    /** Sorts one week's rows into places (pure, so it can be tested). */
    fun rank(rows: Map<String, WeeklyVisit>): List<Entry> =
        rows.filter { it.value.visits > 0 }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, WeeklyVisit>> { it.value.visits }.thenBy { it.value.lastVisitAt })
            .mapIndexed { i, (uid, v) -> Entry(uid, v.userName, v.avatar, v.rank, v.visits, v.lastVisitAt, i + 1) }

    private fun rowsOf(snapshot: DataSnapshot): Map<String, WeeklyVisit> =
        snapshot.children.mapNotNull { c -> c.key?.let { k -> c.getValue(WeeklyVisit::class.java)?.let { k to it } } }.toMap()

    /** Live ranking for [weekKey]: the top [limit] and the signed-in customer's own entry (null = no visits yet). */
    fun listenWeek(weekKey: String, myUid: String, limit: Int = 20, onUpdate: (top: List<Entry>, me: Entry?) -> Unit): ValueEventListener {
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val all = rank(rowsOf(snapshot))
                onUpdate(all.take(limit), all.firstOrNull { it.uid == myUid })
            }
            override fun onCancelled(error: DatabaseError) = onUpdate(emptyList(), null)
        }
        db.child("weeklyVisits").child(weekKey).addValueEventListener(l)
        return l
    }

    fun stopListening(weekKey: String, listener: ValueEventListener?) {
        if (listener != null) db.child("weeklyVisits").child(weekKey).removeEventListener(listener)
    }

    // ── Top-3 rewards ─────────────────────────────────────────────────────────

    /** An unclaimed top-3 place in a finished week. */
    data class Prize(val weekKey: String, val place: Int) {
        val xp: Int get() = AppConfig.LEADERBOARD_XP[place - 1]
    }

    /** The last [AppConfig.LEADERBOARD_CLAIM_WEEKS] finished weeks, newest first. */
    fun claimableWeeks(now: Long = ServerClock.now()): List<String> =
        (1..AppConfig.LEADERBOARD_CLAIM_WEEKS).map { MissionDateUtils.getWeekYearKey(now - it * 7L * 24 * 60 * 60 * 1000) }

    /** Finds [uid]'s unclaimed top-3 places in the claimable weeks (newest first). */
    fun findPrizes(uid: String, onResult: (List<Prize>) -> Unit) {
        if (uid.isBlank()) return onResult(emptyList())
        db.child("xpEvents").child(uid).get().addOnSuccessListener { events ->
            val weeks = claimableWeeks()
            val found = mutableListOf<Prize>()
            var pending = weeks.size
            fun done() { if (--pending == 0) onResult(found.sortedByDescending { it.weekKey }) }
            weeks.forEach { wk ->
                if (events.hasChild(XpEventKey.leaderboard(wk))) return@forEach done()
                db.child("weeklyVisits").child(wk).get()
                    .addOnSuccessListener { snap ->
                        val place = rank(rowsOf(snap)).firstOrNull { it.uid == uid }?.place
                        if (place != null && place <= AppConfig.LEADERBOARD_XP.size) synchronized(found) { found += Prize(wk, place) }
                        done()
                    }
                    .addOnFailureListener { done() }
            }
        }.addOnFailureListener { onResult(emptyList()) }
    }

    /** Pays [prize] and unlocks Juara Minggu (if not yet) in one write; payable once per week. */
    fun claim(uid: String, prize: Prize, onResult: (XpRepository.Award?) -> Unit) {
        BadgeRepository.readContext(uid) { _, badges ->
            val extra = hashMapOf<String, Any>()
            val hasBadge = (badges?.child(BadgeDef.JUARA_MINGGU.id)?.child("unlockedAt")?.getValue(Long::class.java) ?: 0L) > 0L
            if (!hasBadge) extra["badges/$uid/${BadgeDef.JUARA_MINGGU.id}/unlockedAt"] = ServerClock.now()
            XpRepository.award(uid, XpEventKey.leaderboard(prize.weekKey), prize.xp, XpSource.LEADERBOARD, extra, onResult)
        }
    }
}
