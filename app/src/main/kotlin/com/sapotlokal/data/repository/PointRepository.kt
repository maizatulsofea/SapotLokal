package com.sapotlokal.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.data.model.Point
import com.sapotlokal.data.model.PointSource
import com.sapotlokal.data.model.XpEventKey
import com.sapotlokal.data.model.XpSource
import com.sapotlokal.util.ServerClock

/**
 * PointRepository — all reads and writes to the /points node in Firebase Realtime Database.
 *
 * WHY A REPOSITORY: The build guide (Rule 9) says every points change is a ledger row.
 * Centralising those writes here means every screen uses the same logic — no duplicated
 * Firebase paths or business rules scattered across Activities.
 *
 * Shop points only (REFRACTOR.md §1.2): every row belongs to one shop. Visits earn them (the
 * shop's points per visit × the customer's rank bonus); claiming a reward spends them. XP is a
 * separate thing, written only by XpRepository.
 */
object PointRepository {

    private val db = com.google.firebase.database.FirebaseDatabase
        .getInstance(AppConfig.DATABASE_URL)
        .reference

    // ── Write ──────────────────────────────────────────────────────────────────

    /** What one valid scan gave: shop points (with the rank bonus), XP, and the rank after it. */
    data class VisitResult(
        val points: Int,
        val basePoints: Int,
        val rankBonus: Double,
        val xp: Int,
        val rankBefore: AppConfig.Rank,
        val rankAfter: AppConfig.Rank,
        val badgesUnlocked: List<com.sapotlokal.data.model.BadgeDef> = emptyList()
    )

    /**
     * Records one shop visit in ONE atomic write (REFRACTOR.md §4.1):
     *   points/{id}                     shop points = shop's pointsPerVisit × the customer's rank bonus
     *   xpEvents + users/xp + users/rank AppConfig.VISIT_XP, flat (see XpRepository.awardPaths)
     *   userStats/{uid}                 totalVisits +1, shopsVisited/{vendorID}, lastVisitAt
     *   weeklyVisits/{week}/{uid}       visits +1 for the leaderboard
     * The caller checks the cooldown first (hasRecentShopVisit). [onResult] gets null on failure.
     */
    fun recordShopVisit(
        userID: String,
        vendorID: String,
        onResult: (VisitResult?) -> Unit
    ) {
        if (userID.isBlank() || vendorID.isBlank()) return onResult(null)
        db.child("users").child(userID).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(user: DataSnapshot) {
                db.child("vendors").child(vendorID).child("pointsPerVisit")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(rate: DataSnapshot) {
                            val perVisit = rate.getValue(Long::class.java)?.toInt()
                                ?.takeIf { it in AppConfig.ALLOWED_POINTS_PER_VISIT }
                                ?: AppConfig.DEFAULT_POINTS_PER_VISIT
                            BadgeRepository.readContext(userID) { stats, badges ->
                                writeVisit(userID, vendorID, perVisit, user, stats, badges, onResult)
                            }
                        }
                        override fun onCancelled(error: DatabaseError) = onResult(null)
                    })
            }
            override fun onCancelled(error: DatabaseError) = onResult(null)
        })
    }

    private fun writeVisit(
        userID: String,
        vendorID: String,
        perVisit: Int,
        user: DataSnapshot,
        stats: DataSnapshot?,
        badges: DataSnapshot?,
        onResult: (VisitResult?) -> Unit
    ) {
        val pointID = db.child("points").push().key ?: return onResult(null)
        val now = ServerClock.now()
        val standing = XpRepository.standingOf(user)
        val rank = standing.rank
        val points = AppConfig.shopPointsForVisit(perVisit, rank)
        val (xpPaths, rankAfter) = XpRepository.awardPaths(
            uid = userID,
            eventKey = XpEventKey.visit(pointID),
            amount = AppConfig.VISIT_XP,
            source = XpSource.VISIT,
            current = standing,
            now = now
        )
        val week = com.sapotlokal.util.MissionDateUtils.getWeekYearKey(now)
        val updates = hashMapOf<String, Any>(
            "points/$pointID" to Point(
                pointID = pointID,
                userID = userID,
                vendorID = vendorID,
                pointEarnedAmount = points,
                pointRedeemedAmount = 0,
                pointSource = PointSource.SHOP_VISIT,
                timestamp = now,
                basePoints = perVisit,
                rankBonus = rank.pointsBonus
            ),
            "userStats/$userID/totalVisits" to ServerValue.increment(1),
            "userStats/$userID/shopsVisited/$vendorID" to true,
            "userStats/$userID/lastVisitAt" to now,
            "weeklyVisits/$week/$userID/visits" to ServerValue.increment(1),
            "weeklyVisits/$week/$userID/lastVisitAt" to now,
            "weeklyVisits/$week/$userID/userName" to (user.child("userName").getValue(String::class.java) ?: "").take(60),
            "weeklyVisits/$week/$userID/avatar" to (user.child("avatar").getValue(String::class.java) ?: ""),
            "weeklyVisits/$week/$userID/rank" to rankAfter.level
        )
        updates.putAll(xpPaths)
        updates.putAll(MissionRepository.visitPaths(userID, vendorID, week))
        val (badgePaths, unlocked) = BadgeRepository.visitPaths(userID, vendorID, now, stats, badges)
        updates.putAll(badgePaths)

        db.updateChildren(updates)
            .addOnSuccessListener {
                onResult(VisitResult(points, perVisit, rank.pointsBonus, AppConfig.VISIT_XP, rank, rankAfter, unlocked))
            }
            .addOnFailureListener { onResult(null) }
    }

    /**
     * True when [userID] already earned a shop-visit reward at [vendorID] inside the cooldown
     * window (AppConfig.SHOP_VISIT_COOLDOWN_MINUTES), judged from the live ledger.
     * A cooldown of 0 disables the check (useful while testing).
     */
    fun hasRecentShopVisit(
        userID: String,
        vendorID: String,
        onResult: (recent: Boolean) -> Unit,
        onError: (String) -> Unit
    ) {
        val cooldownMs = AppConfig.SHOP_VISIT_COOLDOWN_MINUTES * 60_000L
        if (cooldownMs <= 0L) {
            onResult(false)
            return
        }
        db.child("points").orderByChild("userID").equalTo(userID)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val cutoff = ServerClock.now() - cooldownMs
                    val recent = snapshot.children.any { child ->
                        val p = child.getValue(Point::class.java) ?: return@any false
                        p.pointSource == PointSource.SHOP_VISIT &&
                            p.vendorID == vendorID &&
                            p.timestamp >= cutoff
                    }
                    onResult(recent)
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }

    // ── Read — one-shot & live ──────────────────────────────────────────────────

    /**
     * Reads all point rows for [userID] once and calls back with:
     *   - perShopBalance: Map<vendorID, balance>
     *   - lifetimeEarned: Int — used for tier progression
     *   - spendableBalance: Int — available points for redemption
     */
    fun fetchAllPointsForUser(
        userID: String,
        onResult: (perShopBalance: Map<String, Int>, lifetimeEarned: Int, spendableBalance: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        db.child("points")
            .orderByChild("userID")
            .equalTo(userID)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val perShop = mutableMapOf<String, Int>()
                    var lifetimeEarned = 0
                    var totalRedeemed = 0

                    for (child in snapshot.children) {
                        val p = child.getValue(Point::class.java) ?: continue
                        lifetimeEarned += p.pointEarnedAmount
                        totalRedeemed += p.pointRedeemedAmount
                        if (p.vendorID.isNotBlank()) {
                            val current = perShop.getOrDefault(p.vendorID, 0)
                            perShop[p.vendorID] =
                                current + p.pointEarnedAmount - p.pointRedeemedAmount
                        }
                    }

                    val spendableBalance = (lifetimeEarned - totalRedeemed).coerceAtLeast(0)
                    onResult(perShop, lifetimeEarned, spendableBalance)
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }

    fun fetchAllPointsForUser(
        userID: String,
        onResult: (perShopBalance: Map<String, Int>, lifetimeEarned: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        fetchAllPointsForUser(
            userID = userID,
            onResult = { perShop, lifetime, _ -> onResult(perShop, lifetime) },
            onError = onError
        )
    }

    /**
     * Reads all point rows for [userID] and streams live updates.
     * Used by CustomerHomeActivity so the balances update in real-time.
     */
    fun listenToPointsForUser(
        userID: String,
        onUpdate: (perShopBalance: Map<String, Int>, lifetimeEarned: Int, spendableBalance: Int) -> Unit,
        onError: (String) -> Unit,
        onWeekly: ((earnedThisWeek: Int) -> Unit)? = null,
        onVisits: ((lastVisitAt: Map<String, Long>) -> Unit)? = null
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val perShop = mutableMapOf<String, Int>()
                // Every shop with a visit, even when its points are all spent: "Visited" means been there.
                val lastVisit = mutableMapOf<String, Long>()
                var lifetimeEarned = 0
                var weekly = 0
                val weekStart = com.sapotlokal.util.MissionDateUtils.getWeekStartMillis()

                var totalRedeemed = 0

                for (child in snapshot.children) {
                    val p = child.getValue(Point::class.java) ?: continue
                    if (p.timestamp >= weekStart) weekly += p.pointEarnedAmount
                    lifetimeEarned += p.pointEarnedAmount
                    totalRedeemed += p.pointRedeemedAmount
                    if (p.vendorID.isNotBlank() && p.pointSource == PointSource.SHOP_VISIT) {
                        lastVisit[p.vendorID] = maxOf(lastVisit[p.vendorID] ?: 0L, p.timestamp)
                    }
                    if (p.vendorID.isNotBlank()) {
                        val current = perShop.getOrDefault(p.vendorID, 0)
                        perShop[p.vendorID] =
                            current + p.pointEarnedAmount - p.pointRedeemedAmount
                    }
                }

                val spendableBalance = (lifetimeEarned - totalRedeemed).coerceAtLeast(0)
                onWeekly?.invoke(weekly)
                onVisits?.invoke(lastVisit)
                onUpdate(perShop, lifetimeEarned, spendableBalance)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }

        db.child("points")
            .orderByChild("userID")
            .equalTo(userID)
            .addValueEventListener(listener)

        return listener
    }

    fun listenToPointsForUser(
        userID: String,
        onUpdate: (perShopBalance: Map<String, Int>, lifetimeEarned: Int) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        return listenToPointsForUser(
            userID = userID,
            onUpdate = { perShop, lifetime, _ -> onUpdate(perShop, lifetime) },
            onError = onError
        )
    }

    /**
     * Removes a live listener previously returned by [listenToPointsForUser].
     * Call this in the Activity's onStop() to stop unnecessary reads.
     */
    fun removePointsListener(userID: String, listener: ValueEventListener) {
        db.child("points")
            .orderByChild("userID")
            .equalTo(userID)
            .removeEventListener(listener)
    }
}
