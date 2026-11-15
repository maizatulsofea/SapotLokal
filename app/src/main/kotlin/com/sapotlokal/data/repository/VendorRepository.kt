package com.sapotlokal.data.repository

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.data.model.Vendor

/**
 * VendorRepository — reads from the /vendors node.
 *
 * Phase 3 only needs to list all vendors (Find Shops screen).
 * Phase 4 will add writes (create/edit vendor QR, reward rules).
 */
object VendorRepository {

    private val db = com.google.firebase.database.FirebaseDatabase
        .getInstance(AppConfig.DATABASE_URL)
        .reference

    /**
     * Fetches the list of all registered vendors once.
     * Used by FindShopsActivity to populate the RecyclerView.
     */
    fun fetchAllVendors(
        onResult: (List<Vendor>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.child("vendors")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val vendors = snapshot.children
                        .mapNotNull { it.getValue(Vendor::class.java) }
                    onResult(vendors)
                }
                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }

    /**
     * Fetches a single vendor by ID once.
     * Used when we need the vendor name/category after scanning a QR.
     */
    fun fetchVendorById(
        vendorID: String,
        onResult: (Vendor?) -> Unit,
        onError: (String) -> Unit
    ) {
        db.child("vendors").child(vendorID)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    onResult(snapshot.getValue(Vendor::class.java))
                }
                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }

    /**
     * Listens live to updates for a single vendor by ID.
     * Used by ShopDetailActivity to track live OPEN/CLOSED status and operating hours.
     */
    fun listenToVendor(
        vendorID: String,
        onUpdate: (Vendor?) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onUpdate(snapshot.getValue(Vendor::class.java))
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }
        db.child("vendors").child(vendorID).addValueEventListener(listener)
        return listener
    }

    /**
     * Listens live to all vendors.
     * Used by FindShopsActivity so live status chips update in real-time.
     */
    fun listenToAllVendors(
        onUpdate: (List<Vendor>) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val vendors = snapshot.children.mapNotNull { it.getValue(Vendor::class.java) }
                onUpdate(vendors)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error.message)
            }
        }
        db.child("vendors").addValueEventListener(listener)
        return listener
    }

    /**
     * Detaches a listener from a specific vendor node.
     */
    fun removeVendorListener(vendorID: String, listener: ValueEventListener) {
        db.child("vendors").child(vendorID).removeEventListener(listener)
    }

    /**
     * Detaches a listener from the /vendors node.
     */
    fun removeAllVendorsListener(listener: ValueEventListener) {
        db.child("vendors").removeEventListener(listener)
    }

    /**
     * Looks up the vendorID that belongs to a given userID.
     * Used in Phase 4 when a Shop Owner logs in to find their own vendor record.
     */
    fun fetchVendorByUserId(
        userID: String,
        onResult: (Vendor?) -> Unit,
        onError: (String) -> Unit
    ) {
        db.child("vendors")
            .orderByChild("userID")
            .equalTo(userID)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val vendor = snapshot.children.firstOrNull()
                        ?.getValue(Vendor::class.java)
                    onResult(vendor)
                }
                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }

    /**
     * Updates an existing vendor profile in /vendors/{vendorID}.
     * Only fields like vendorName, vendorCategory, vendorAddress, vendorDescription are modified.
     */
    fun updateVendor(
        vendor: Vendor,
        onResult: (success: Boolean, errorMsg: String?) -> Unit
    ) {
        if (vendor.vendorID.isBlank()) {
            onResult(false, "Invalid vendor ID")
            return
        }

        val updates = mutableMapOf<String, Any>(
            "vendorName" to vendor.vendorName,
            "vendorCategory" to vendor.vendorCategory,
            "vendorAddress" to vendor.vendorAddress,
            "vendorDescription" to vendor.vendorDescription,
            "operatingHours" to vendor.operatingHours,
            "pointsPerVisit" to vendor.pointsPerVisit
        )

        db.child("vendors").child(vendor.vendorID)
            .updateChildren(updates)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    /**
     * Toggles vendor open/closed status and records timestamp.
     */
    /** Saves just the shop's points per visit (one of AppConfig.ALLOWED_POINTS_PER_VISIT). */
    fun setPointsPerVisit(vendorID: String, value: Int, onResult: (success: Boolean) -> Unit) {
        if (vendorID.isBlank() || value !in AppConfig.ALLOWED_POINTS_PER_VISIT) return onResult(false)
        db.child("vendors").child(vendorID).child("pointsPerVisit").setValue(value)
            .addOnCompleteListener { onResult(it.isSuccessful) }
    }

    fun updateVendorStatus(
        vendorID: String,
        isOpen: Boolean,
        onResult: (success: Boolean, errorMsg: String?) -> Unit
    ) {
        if (vendorID.isBlank()) {
            onResult(false, "Invalid vendor ID")
            return
        }

        val updates = mapOf<String, Any>(
            "isOpen" to isOpen,
            "lastStatusChangedAt" to com.google.firebase.database.ServerValue.TIMESTAMP
        )

        db.child("vendors").child(vendorID)
            .updateChildren(updates)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    /**
     * Dispatches an FCM topic notification broadcast event when a shop opens.
     * Records event under /shopNotifications for tracking, live dispatch, and FCM cloud hooks.
     */
    fun broadcastShopStatus(
        vendorID: String,
        vendorName: String,
        isOpen: Boolean,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (vendorID.isBlank()) {
            onComplete(false)
            return
        }

        val notifRef = db.child("shopNotifications").push()
        val title = if (isOpen) "$vendorName is now OPEN! 🟢" else "$vendorName is now CLOSED 🔴"
        val body = if (isOpen) {
            "$vendorName is now OPEN! Drop by today to earn your loyalty points."
        } else {
            "$vendorName has just closed. We'll let you know when it opens again."
        }
        val payload = mapOf(
            "notificationId" to (notifRef.key ?: ""),
            "vendorID" to vendorID,
            "vendorId" to vendorID,
            "vendorName" to vendorName,
            "type" to "shop_status",
            "isOpen" to isOpen,
            "topic" to "shop_$vendorID",
            "title" to title,
            "body" to body,
            "timestamp" to com.google.firebase.database.ServerValue.TIMESTAMP
        )

        notifRef.setValue(payload)
            .addOnSuccessListener {
                android.util.Log.i("VendorRepository", "FCM topic broadcast dispatched for /topics/shop_$vendorID ($vendorName)")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                android.util.Log.w("VendorRepository", "FCM topic broadcast logging failed", e)
                onComplete(false)
            }
    }

    /**
     * Live numbers for the shop owner's Home (this week's visits and points, vouchers used,
     * recent visits). Updates by itself when a customer scans.
     */
    /** The two live listeners behind the owner Home stats; remove both with [removeAnalyticsListener]. */
    class AnalyticsListener internal constructor(
        internal val points: ValueEventListener,
        internal val vouchers: ValueEventListener
    )

    /**
     * Live stats for the owner Home: recomputed when a visit lands in /points or a voucher is
     * marked used in /voucherUsers (which writes nothing to /points).
     */
    fun listenToAnalytics(
        vendorID: String,
        onUpdate: (com.sapotlokal.util.VendorAnalytics) -> Unit,
        onError: (String) -> Unit
    ): AnalyticsListener {
        var points: List<com.sapotlokal.data.model.Point>? = null
        var used: List<Long>? = null
        fun publish() {
            val p = points ?: return
            val u = used ?: return
            onUpdate(com.sapotlokal.util.VendorAnalyticsCalculator.compute(p, u))
        }
        val pointsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                points = snapshot.children.mapNotNull { it.getValue(com.sapotlokal.data.model.Point::class.java) }
                publish()
            }
            override fun onCancelled(error: DatabaseError) = onError(error.message)
        }
        val vouchersListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                used = snapshot.children.filter {
                    it.child("voucherStatus").getValue(String::class.java)
                        .equals(com.sapotlokal.data.model.VoucherStatus.USED, ignoreCase = true)
                }.mapNotNull { it.child("voucherUsedAt").getValue(Long::class.java) }
                publish()
            }
            override fun onCancelled(error: DatabaseError) {
                used = emptyList()
                publish()
            }
        }
        db.child("points").orderByChild("vendorID").equalTo(vendorID).addValueEventListener(pointsListener)
        db.child("voucherUsers").orderByChild("vendorID").equalTo(vendorID).addValueEventListener(vouchersListener)
        return AnalyticsListener(pointsListener, vouchersListener)
    }

    fun removeAnalyticsListener(vendorID: String, listener: AnalyticsListener) {
        db.child("points").orderByChild("vendorID").equalTo(vendorID).removeEventListener(listener.points)
        db.child("voucherUsers").orderByChild("vendorID").equalTo(vendorID).removeEventListener(listener.vouchers)
    }
}
