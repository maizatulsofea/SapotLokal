package com.sapotlokal.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.AppConfig.Rank
import com.sapotlokal.data.model.XpEvent
import com.sapotlokal.util.ServerClock

/**
 * XpRepository — the customer's XP and rank (REFRACTOR.md §3, §4). The ONLY place XP is written.
 *
 * XP is never spent and never goes down; the rank comes from it. Every XP award is three paths
 * written in the same updateChildren as whatever the award is for:
 *   xpEvents/{uid}/{eventKey}   the event (key is fixed, so the rules let it be written once)
 *   users/{uid}/xp              ServerValue.increment(amount)
 *   users/{uid}/rank            the rank after the award
 *
 * users/{uid}/rankSeen is the highest rank the customer has been shown a rank-up sheet for.
 */
object XpRepository {

    private val db = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference

    /** A customer's XP, the rank it gives, and the highest rank they've already been told about (0 = never set). */
    data class Standing(val xp: Int, val rank: Rank, val rankSeen: Int = 0)

    fun standingOf(user: DataSnapshot): Standing {
        val xp = user.child("xp").getValue(Long::class.java)?.toInt() ?: 0
        val seen = user.child("rankSeen").getValue(Long::class.java)?.toInt() ?: 0
        return Standing(xp, Rank.fromXp(xp), seen)
    }

    /** Reads [uid]'s XP once. Falls back to rank 1 if it can't be read, so screens still draw. */
    fun fetchStanding(uid: String, onResult: (Standing) -> Unit) {
        if (uid.isBlank()) return onResult(Standing(0, Rank.JIRAN_BARU))
        db.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = onResult(standingOf(snapshot))
            override fun onCancelled(error: DatabaseError) = onResult(Standing(0, Rank.JIRAN_BARU))
        })
    }

    /** Live XP for [uid]. Remove with [stopListening]. */
    fun listenStanding(uid: String, onUpdate: (Standing) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = onUpdate(standingOf(snapshot))
            override fun onCancelled(error: DatabaseError) {}
        }
        if (uid.isNotBlank()) db.child("users").child(uid).addValueEventListener(listener)
        return listener
    }

    fun stopListening(uid: String, listener: ValueEventListener?) {
        if (uid.isNotBlank() && listener != null) db.child("users").child(uid).removeEventListener(listener)
    }

    /** Records that the customer has seen the sheet for [rank] (or quietly starts the record for an existing customer). */
    fun markRankSeen(uid: String, rank: Rank) {
        if (uid.isNotBlank()) db.child("users").child(uid).child("rankSeen").setValue(rank.level)
    }

    /**
     * The paths that award [amount] XP to [uid] for the event [eventKey], given the customer's
     * [current] standing. Add them to the same updateChildren as the thing being rewarded.
     * Returns the paths and the rank after the award.
     */
    fun awardPaths(
        uid: String,
        eventKey: String,
        amount: Int,
        source: String,
        current: Standing,
        now: Long = ServerClock.now()
    ): Pair<Map<String, Any>, Rank> {
        val after = Rank.fromXp(current.xp + amount)
        return mapOf(
            "xpEvents/$uid/$eventKey" to XpEvent(amount = amount, source = source, timestamp = now),
            "users/$uid/xp" to ServerValue.increment(amount.toLong()),
            "users/$uid/rank" to after.level
        ) to after
    }

    /** What an award did. */
    data class Award(val amount: Int, val rankBefore: Rank, val rankAfter: Rank)

    /**
     * Awards [amount] XP to [uid] for [eventKey], together with [extraUpdates] (for example a
     * mission's claimedAt), in ONE atomic write. The rules reject a repeated [eventKey], so the
     * same reward can't be paid twice. [onResult] gets null if the write failed.
     */
    fun award(
        uid: String,
        eventKey: String,
        amount: Int,
        source: String,
        extraUpdates: Map<String, Any> = emptyMap(),
        onResult: (Award?) -> Unit = {}
    ) {
        if (uid.isBlank() || amount <= 0) return onResult(null)
        fetchStanding(uid) { standing ->
            val now = ServerClock.now()
            val (paths, after) = awardPaths(uid, eventKey, amount, source, standing, now)
            val updates = HashMap<String, Any>(extraUpdates)
            updates.putAll(paths)
            db.updateChildren(updates)
                .addOnSuccessListener { onResult(Award(amount, standing.rank, after)) }
                .addOnFailureListener { onResult(null) }
        }
    }
}
