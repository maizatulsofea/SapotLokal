package com.sapotlokal.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.data.model.BadgeDef
import com.sapotlokal.data.model.BadgeRules
import com.sapotlokal.data.model.BadgeState
import com.sapotlokal.data.model.GameKind
import com.sapotlokal.data.model.XpEventKey
import com.sapotlokal.data.model.XpSource
import com.sapotlokal.util.MissionDateUtils
import com.sapotlokal.util.ServerClock

/**
 * BadgeRepository — badges (REFRACTOR.md §3.5, Phase 6).
 *
 * A badge is unlocked inside the write that earns it: the scan and game writes call [visitPaths] /
 * [gamePaths] with the customer's current /userStats and /badges, and add the returned paths to
 * their own updateChildren. Unlocking only records the moment; the XP is paid when the customer
 * claims the badge ([claim]), once, through XpRepository.award.
 *
 * Extra tracking kept in /userStats/{uid} for the badges:
 *   days/{yyyy-MM-dd}/{visit|spin|memory} = true          → Combo
 *   shopDays/{weekKey}/{vendorID}/{yyyy-MM-dd} = true       → Regular
 */
object BadgeRepository {

    private val db = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference

    /** Reads /userStats/{uid} and /badges/{uid} once, then calls [then] with both. */
    fun readContext(uid: String, then: (stats: DataSnapshot?, badges: DataSnapshot?) -> Unit) {
        db.child("userStats").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(stats: DataSnapshot) {
                db.child("badges").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(badges: DataSnapshot) = then(stats, badges)
                    override fun onCancelled(error: DatabaseError) = then(stats, null)
                })
            }
            override fun onCancelled(error: DatabaseError) = then(null, null)
        })
    }

    private fun keys(snapshot: DataSnapshot?): Set<String> =
        snapshot?.children?.mapNotNull { it.key }?.toSet() ?: emptySet()

    private fun owned(badges: DataSnapshot?): Set<String> =
        badges?.children?.filter { (it.child("unlockedAt").getValue(Long::class.java) ?: 0L) > 0L }
            ?.mapNotNull { it.key }?.toSet() ?: emptySet()

    private fun unlockPaths(uid: String, now: Long, earned: List<BadgeDef>): Map<String, Any> =
        earned.associate { "badges/$uid/${it.id}/unlockedAt" to now }

    /** Tracking and unlocks for a valid shop visit at [vendorID]. */
    fun visitPaths(uid: String, vendorID: String, now: Long, stats: DataSnapshot?, badges: DataSnapshot?): Pair<Map<String, Any>, List<BadgeDef>> {
        val day = BadgeRules.dayKey(now)
        val week = MissionDateUtils.getWeekYearKey(now)
        val earned = BadgeRules.onVisit(
            now = now,
            totalVisits = stats?.child("totalVisits")?.getValue(Long::class.java) ?: 0L,
            shopDaysThisWeek = keys(stats?.child("shopDays")?.child(week)?.child(vendorID)),
            todayMarks = keys(stats?.child("days")?.child(day)),
            owned = owned(badges)
        )
        val paths = hashMapOf<String, Any>(
            "userStats/$uid/days/$day/visit" to true,
            "userStats/$uid/shopDays/$week/$vendorID/$day" to true
        )
        paths.putAll(unlockPaths(uid, now, earned))
        return paths to earned
    }

    /** Tracking and unlocks for a game play; see [BadgeRules.onGame]. */
    fun gamePaths(
        uid: String, kind: GameKind, now: Long, stats: DataSnapshot?, badges: DataSnapshot?,
        topSegment: Boolean = false, perfect: Boolean = false
    ): Pair<Map<String, Any>, List<BadgeDef>> {
        val day = BadgeRules.dayKey(now)
        val earned = BadgeRules.onGame(kind, keys(stats?.child("days")?.child(day)), owned(badges), topSegment, perfect)
        val paths = hashMapOf<String, Any>("userStats/$uid/days/$day/${kind.mark}" to true)
        paths.putAll(unlockPaths(uid, now, earned))
        return paths to earned
    }

    /** Unlocks [def] on its own write (for badges earned outside a scan or game, e.g. Belanja Diri). */
    fun unlockNow(uid: String, def: BadgeDef) {
        val ref = db.child("badges").child(uid).child(def.id).child("unlockedAt")
        ref.get().addOnSuccessListener { if (!it.exists()) ref.setValue(ServerClock.now()) }
    }

    // ── Reading and claiming ──────────────────────────────────────────────────

    fun stateFrom(badges: DataSnapshot?): List<BadgeState> = BadgeDef.entries.map { def ->
        val b = badges?.child(def.id)
        BadgeState(
            def,
            b?.child("unlockedAt")?.getValue(Long::class.java) ?: 0L,
            b?.child("claimedAt")?.getValue(Long::class.java) ?: 0L
        )
    }

    /** Live badges for [uid]. Remove with [stopListening]. */
    fun listen(uid: String, onUpdate: (List<BadgeState>) -> Unit): ValueEventListener {
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = onUpdate(stateFrom(snapshot))
            override fun onCancelled(error: DatabaseError) {}
        }
        if (uid.isNotBlank()) db.child("badges").child(uid).addValueEventListener(l)
        return l
    }

    fun stopListening(uid: String, listener: ValueEventListener?) {
        if (uid.isNotBlank() && listener != null) db.child("badges").child(uid).removeEventListener(listener)
    }

    /** Pays the badge's XP and marks it claimed in one write; the rules make it payable once. */
    fun claim(uid: String, badge: BadgeState, onResult: (XpRepository.Award?) -> Unit) {
        if (!badge.claimable) return onResult(null)
        XpRepository.award(
            uid = uid,
            eventKey = XpEventKey.badge(badge.def.id),
            amount = badge.def.xp,
            source = XpSource.BADGE,
            extraUpdates = mapOf("badges/$uid/${badge.def.id}/claimedAt" to ServerClock.now()),
            onResult = onResult
        )
    }
}
