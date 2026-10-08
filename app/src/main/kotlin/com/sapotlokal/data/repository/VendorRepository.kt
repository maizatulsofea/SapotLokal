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
}
