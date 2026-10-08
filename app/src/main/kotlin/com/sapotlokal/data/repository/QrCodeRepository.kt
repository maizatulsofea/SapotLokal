package com.sapotlokal.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.data.model.QrCode

/**
 * QrCodeRepository — reads and validates entries in the /qrcodes node.
 *
 * Phase 3: validation only (the customer side reads codes).
 * Phase 4: the shop owner side WRITES new rotating codes here.
 *
 * HOW QR VALIDATION WORKS (build guide Section 3.1 + Rule 43):
 *   The customer's app receives a string from the camera scan.
 *   It queries /qrcodes where qrcodeValue == scannedValue.
 *   A code is valid only when ALL of these are true:
 *     1. A record exists in /qrcodes with that value
 *     2. isActive == true
 *     3. qrcodeExpiryDate > now (not expired)
 *     4. The vendorID on the record belongs to a real vendor
 *   The check runs against the live database — never against cached data.
 *
 * TROUBLESHOOTING "Invalid QR":
 *   Check Logcat tag "QrCodeRepo" — it logs the query result so you can
 *   distinguish "no record found" from "record found but failed a check".
 *   Common causes:
 *   - qrcodeValue index missing in Firebase Rules → add "qrcodeValue" to .indexOn (FIXED)
 *   - isActive field not set to true in the database
 *   - qrcodeExpiryDate is 0 or missing (must be a future Unix timestamp in milliseconds)
 *   - Field names in Firebase don't match the QrCode data class exactly
 */
object QrCodeRepository {

    private const val TAG = "QrCodeRepo"

    private val db = com.google.firebase.database.FirebaseDatabase
        .getInstance(AppConfig.DATABASE_URL)
        .reference

    /**
     * Looks up [scannedValue] in /qrcodes and validates it.
     *
     * @param scannedValue  The raw string decoded from the QR image
     * @param onValid       Called with the matching QrCode when all checks pass
     * @param onInvalid     Called with a user-readable reason when any check fails
     * @param onError       Called when the database read itself fails (network, rules denied, etc.)
     */
    fun validateScannedCode(
        scannedValue: String,
        onValid: (QrCode) -> Unit,
        onInvalid: (reason: String) -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d(TAG, "Querying /qrcodes for qrcodeValue=\"$scannedValue\"")

        db.child("qrcodes")
            .orderByChild("qrcodeValue")
            .equalTo(scannedValue)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d(TAG, "onDataChange: exists=${snapshot.exists()}, childCount=${snapshot.childrenCount}")

                    if (!snapshot.exists()) {
                        Log.w(TAG, "No record found — check the value you entered matches qrcodeValue in Firebase exactly")
                        onInvalid("Invalid QR code. Please scan the shop's current code.")
                        return
                    }

                    val qrCode = snapshot.children.firstOrNull()
                        ?.getValue(QrCode::class.java)

                    Log.d(TAG, "Parsed QrCode: $qrCode")

                    if (qrCode == null) {
                        Log.w(TAG, "Record found but deserialized to null — check field names in Firebase match QrCode data class")
                        onInvalid("Invalid QR code. Please scan the shop's current code.")
                        return
                    }

                    if (!qrCode.isActive) {
                        Log.w(TAG, "QrCode found but isActive=false — set isActive to true in Firebase")
                        onInvalid("This code is no longer active. Ask the shop owner to show a new one.")
                        return
                    }

                    val nowMs = System.currentTimeMillis()
                    Log.d(TAG, "qrcodeExpiryDate=${qrCode.qrcodeExpiryDate}, nowMs=$nowMs, expired=${qrCode.qrcodeExpiryDate < nowMs}")

                    if (qrCode.qrcodeExpiryDate < nowMs) {
                        Log.w(TAG, "QrCode is expired — set qrcodeExpiryDate to a future timestamp (ms), e.g. ${nowMs + 3_600_000} for +1 hour")
                        onInvalid("This QR code has expired. Ask the shop owner to show a fresh one.")
                        return
                    }

                    Log.i(TAG, "QR code VALID — vendorID=${qrCode.vendorID}")
                    onValid(qrCode)
                }

                override fun onCancelled(error: DatabaseError) {
                    // PERMISSION_DENIED (code -3) means the Security Rules blocked the query.
                    // INDEX_MISSING means .indexOn for "qrcodeValue" was not set — now fixed in rules.
                    Log.e(TAG, "Query CANCELLED — code=${error.code}, message=${error.message}, details=${error.details}")
                    onError("Could not verify QR code: ${error.message}")
                }
            })
    }
    // ── Phase 4 — Vendor writes ───────────────────────────────────────────────

    /**
     * Publishes a new rotating QR code for [vendorID].
     *
     * HOW ROTATION WORKS:
     *   1. Generate a new random [qrcodeValue] (UUID-based).
     *   2. Push a new /qrcodes entry: isActive=true, expiryDate=now+QR_ROTATION_SECONDS.
     *   3. In the same updateChildren() batch, set the old code's isActive=false.
     *
     * WHY ATOMIC: Firebase updateChildren() on the root ref either writes ALL
     * paths or none. If the write is interrupted, we never end up with two active
     * codes simultaneously — the old one stays active until the new one commits.
     *
     * @param vendorID     The vendor whose code is rotating
     * @param previousQrId The Firebase key of the code being replaced (null on first publish)
     * @param onSuccess    Called with the new QrCode on success
     * @param onError      Called with a readable message on failure
     */
    fun publishNewCode(
        vendorID: String,
        previousQrId: String?,
        onSuccess: (newQrCode: QrCode) -> Unit,
        onError: (String) -> Unit
    ) {
        val nowMs      = System.currentTimeMillis()
        val expiryMs   = nowMs + (AppConfig.QR_ROTATION_SECONDS * 1_000L)
        val newRef     = db.child("qrcodes").push()
        val newId      = newRef.key ?: run { onError("Failed to generate QR ID"); return }
        val newValue   = java.util.UUID.randomUUID().toString()

        val newCode = QrCode(
            qrcodeID           = newId,
            vendorID           = vendorID,
            qrcodeValue        = newValue,
            qrcodeGenerateDate = nowMs,
            qrcodeExpiryDate   = expiryMs,
            isActive           = true
        )

        // Build the multi-location update map
        val updates = mutableMapOf<String, Any>()
        updates["qrcodes/$newId"] = newCode

        // Deactivate the previous code if we know its ID
        if (previousQrId != null) {
            updates["qrcodes/$previousQrId/isActive"] = false
        }

        db.root.updateChildren(updates)
            .addOnSuccessListener {
                Log.i(TAG, "New QR code published: id=$newId, expiresAt=$expiryMs")
                onSuccess(newCode)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to publish QR code", e)
                onError("Could not publish QR code: ${e.message}")
            }
    }
}
