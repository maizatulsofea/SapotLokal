package com.sapotlokal.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.data.model.Point
import com.sapotlokal.data.model.PointSource

/**
 * PointRepository — all reads and writes to the /points node in Firebase Realtime Database.
 *
 * WHY A REPOSITORY: The build guide (Rule 9) says every points change is a ledger row.
 * Centralising those writes here means every screen uses the same logic — no duplicated
 * Firebase paths or business rules scattered across Activities.
 *
 * BALANCE CALCULATION: Per-shop balance = sum(pointEarnedAmount) − sum(pointRedeemedAmount)
 * for one userID + vendorID pair. Lifetime earned = sum(pointEarnedAmount) across ALL vendors,
 * used only for level calculation (build guide Section 3.3 and Open Decision #12).
 *
 * EVERY READ GOES TO THE DATABASE (build guide Rule 43 — never trust local state).
 */
object PointRepository {

    private val db = com.google.firebase.database.FirebaseDatabase
        .getInstance(AppConfig.DATABASE_URL)
        .reference

    // ── Write ──────────────────────────────────────────────────────────────────

    /**
     * Appends a new earned-points ledger row for a shop visit.
     * Called by ScanActivity after a QR code is validated.
     *
     * @param userID    The logged-in customer's UID
     * @param vendorID  The vendor whose QR was just scanned
     * @param onResult  Callback: true = success, false = failure
     */
    fun recordShopVisit(
        userID: String,
        vendorID: String,
        onResult: (success: Boolean) -> Unit
    ) {
        val pointRef = db.child("points").push()
        val pointID = pointRef.key ?: return onResult(false)

        val row = Point(
            pointID            = pointID,
            userID             = userID,
            vendorID           = vendorID,
            pointEarnedAmount  = AppConfig.POINTS_PER_SHOP_VISIT,
            pointRedeemedAmount = 0,
            pointSource        = PointSource.SHOP_VISIT
        )

        pointRef.setValue(row)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }

    // ── Read — one-shot ────────────────────────────────────────────────────────

    /**
     * Reads all point rows for [userID] once and calls back with:
     *   - perShopBalance: Map<vendorID, balance> — used on Find Shops
     *   - lifetimeEarned: Int — used for level calculation
     *
     * Queries the entire /points node and filters client-side. This is acceptable
     * for the current scale; a production version would index by userID.
     */
    fun fetchAllPointsForUser(
        userID: String,
        onResult: (perShopBalance: Map<String, Int>, lifetimeEarned: Int) -> Unit,
        onError: (String) -> Unit
    ) {
        db.child("points")
            .orderByChild("userID")
            .equalTo(userID)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val perShop = mutableMapOf<String, Int>()
                    var lifetimeEarned = 0

                    for (child in snapshot.children) {
                        val p = child.getValue(Point::class.java) ?: continue
                        lifetimeEarned += p.pointEarnedAmount
                        val current = perShop.getOrDefault(p.vendorID, 0)
                        perShop[p.vendorID] =
                            current + p.pointEarnedAmount - p.pointRedeemedAmount
                    }

                    onResult(perShop, lifetimeEarned)
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }

    /**
     * Reads all point rows for [userID] and streams live updates.
     * Used by CustomerHomeActivity so the balance updates in real-time.
     * Returns the listener so the caller can remove it in onStop().
     */
    fun listenToPointsForUser(
        userID: String,
        onUpdate: (perShopBalance: Map<String, Int>, lifetimeEarned: Int) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val perShop = mutableMapOf<String, Int>()
                var lifetimeEarned = 0

                for (child in snapshot.children) {
                    val p = child.getValue(Point::class.java) ?: continue
                    lifetimeEarned += p.pointEarnedAmount
                    val current = perShop.getOrDefault(p.vendorID, 0)
                    perShop[p.vendorID] =
                        current + p.pointEarnedAmount - p.pointRedeemedAmount
                }

                onUpdate(perShop, lifetimeEarned)
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
