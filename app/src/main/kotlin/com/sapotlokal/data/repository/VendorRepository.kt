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

        val updates = mapOf(
            "vendorName" to vendor.vendorName,
            "vendorCategory" to vendor.vendorCategory,
            "vendorAddress" to vendor.vendorAddress,
            "vendorDescription" to vendor.vendorDescription
        )

        db.child("vendors").child(vendor.vendorID)
            .updateChildren(updates)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    /**
     * Aggregates live statistics for a shop owner:
     * - Customer footfall (distinct user IDs who have visited / earned points at this shop)
     * - Total points distributed (sum of pointEarnedAmount for this vendor)
     * - Total vouchers redeemed (count of vouchers in /voucherUsers with status "used")
     * - Total points redeemed (sum of pointRedeemedAmount for this vendor)
     */
    fun fetchVendorStats(
        vendorID: String,
        onResult: (com.sapotlokal.data.model.VendorStats) -> Unit,
        onError: (String) -> Unit
    ) {
        if (vendorID.isBlank()) {
            onError("Invalid vendor ID")
            return
        }

        db.child("points")
            .orderByChild("vendorID")
            .equalTo(vendorID)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(pointsSnapshot: DataSnapshot) {
                    val userIds = mutableSetOf<String>()
                    var totalEarned = 0
                    var totalRedeemed = 0
                    val pointList = mutableListOf<com.sapotlokal.data.model.Point>()

                    for (child in pointsSnapshot.children) {
                        val p = child.getValue(com.sapotlokal.data.model.Point::class.java) ?: continue
                        pointList.add(p)
                        if (p.userID.isNotBlank()) {
                            userIds.add(p.userID)
                        }
                        totalEarned += p.pointEarnedAmount
                        totalRedeemed += p.pointRedeemedAmount
                    }

                    // Also query /voucherUsers for this vendor to count used vouchers
                    db.child("voucherUsers")
                        .orderByChild("vendorID")
                        .equalTo(vendorID)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(voucherSnapshot: DataSnapshot) {
                                var usedVouchersCount = 0
                                for (child in voucherSnapshot.children) {
                                    val status = child.child("voucherStatus").getValue(String::class.java)
                                    if (status.equals(com.sapotlokal.data.model.VoucherStatus.USED, ignoreCase = true)) {
                                        usedVouchersCount++
                                    }
                                }

                                val stats = com.sapotlokal.data.model.VendorStats(
                                    vendorID = vendorID,
                                    totalCustomers = userIds.size,
                                    totalPointsDistributed = totalEarned,
                                    totalPointsRedeemed = totalRedeemed,
                                    totalVouchersRedeemed = usedVouchersCount,
                                    recentPoints = pointList.reversed().take(20)
                                )
                                onResult(stats)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                // Return stats computed so far even if voucher read failed
                                val stats = com.sapotlokal.data.model.VendorStats(
                                    vendorID = vendorID,
                                    totalCustomers = userIds.size,
                                    totalPointsDistributed = totalEarned,
                                    totalPointsRedeemed = totalRedeemed,
                                    totalVouchersRedeemed = 0,
                                    recentPoints = pointList.reversed().take(20)
                                )
                                onResult(stats)
                            }
                        })
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
    }
}
