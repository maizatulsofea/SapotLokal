package com.sapotlokal.data.repository

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.sapotlokal.AppConfig
import com.sapotlokal.data.model.Point
import com.sapotlokal.data.model.PointSource
import com.sapotlokal.data.model.Voucher
import com.sapotlokal.data.model.VoucherUser
import com.sapotlokal.data.model.VoucherStatus

/**
 * VoucherRepository — reads and writes /vouchers (reward rules) and /voucherUsers.
 *
 * VOCABULARY REMINDER (build guide Section 3.2):
 *   /vouchers       = "reward rules" created by the shop owner
 *   /voucherUsers   = per-customer vouchers auto-created when a threshold is crossed
 *
 * REDEMPTION ATOMICITY (Phase 4, build guide Section 4):
 *   Marking a voucher used AND writing the pointRedeemedAmount ledger row happen
 *   in a single updateChildren() call on the database root. Either both succeed or
 *   neither does — there is no half-finished redemption state.
 */
object VoucherRepository {

    private const val TAG = "VoucherRepo"

    private val db = com.google.firebase.database.FirebaseDatabase
        .getInstance(AppConfig.DATABASE_URL)
        .reference

    // ── Reward rules (VOUCHER entity) — vendor writes ─────────────────────────

    /**
     * Fetches all reward rules for [vendorID] once.
     * Used by RewardRulesActivity to populate the list.
     */
    fun fetchRulesForVendor(
        vendorID: String,
        onResult: (List<Voucher>) -> Unit,
        onError: (String) -> Unit
    ) {
        db.child("vouchers")
            .orderByChild("vendorID")
            .equalTo(vendorID)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val rules = snapshot.children
                        .mapNotNull { it.getValue(Voucher::class.java) }
                    onResult(rules)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "fetchRulesForVendor cancelled: ${error.message}")
                    onError(error.message)
                }
            })
    }

    /**
     * Listens live to all reward rules for [vendorID].
     * Returns the listener so the caller can detach it in onStop().
     */
    fun listenToRulesForVendor(
        vendorID: String,
        onUpdate: (List<Voucher>) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val rules = snapshot.children
                    .mapNotNull { it.getValue(Voucher::class.java) }
                onUpdate(rules)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "listenToRulesForVendor cancelled: ${error.message}")
                onError(error.message)
            }
        }
        db.child("vouchers")
            .orderByChild("vendorID")
            .equalTo(vendorID)
            .addValueEventListener(listener)
        return listener
    }

    fun removeRulesListener(vendorID: String, listener: ValueEventListener) {
        db.child("vouchers")
            .orderByChild("vendorID")
            .equalTo(vendorID)
            .removeEventListener(listener)
    }

    /**
     * Creates a new reward rule. Called by AddEditRuleActivity when adding.
     */
    fun createRule(
        vendorID: String,
        voucherName: String,
        voucherDescription: String,
        pointsRequired: Int,
        onResult: (success: Boolean) -> Unit
    ) {
        val ref = db.child("vouchers").push()
        val id  = ref.key ?: return onResult(false)

        val rule = Voucher(
            voucherID          = id,
            vendorID           = vendorID,
            voucherName        = voucherName,
            voucherDescription = voucherDescription,
            pointsRequired     = pointsRequired,
            isActive           = true
        )

        ref.setValue(rule)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { Log.e(TAG, "createRule failed", it); onResult(false) }
    }

    /**
     * Updates an existing reward rule. Called by AddEditRuleActivity when editing.
     * Only the editable fields are changed — vendorID is never touched.
     */
    fun updateRule(
        voucherID: String,
        voucherName: String,
        voucherDescription: String,
        pointsRequired: Int,
        onResult: (success: Boolean) -> Unit
    ) {
        val updates = mapOf<String, Any>(
            "voucherName"        to voucherName,
            "voucherDescription" to voucherDescription,
            "pointsRequired"     to pointsRequired
        )
        db.child("vouchers").child(voucherID).updateChildren(updates)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { Log.e(TAG, "updateRule failed", it); onResult(false) }
    }

    /**
     * Toggles the isActive flag on a reward rule.
     * Turning a rule off stops new VOUCHER_USER records from being created for it,
     * but already-issued customer vouchers for that rule remain valid (build guide Open Decision #11).
     */
    fun setRuleActive(
        voucherID: String,
        isActive: Boolean,
        onResult: (success: Boolean) -> Unit
    ) {
        db.child("vouchers").child(voucherID).child("isActive").setValue(isActive)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { Log.e(TAG, "setRuleActive failed", it); onResult(false) }
    }

    // ── Customer vouchers (VOUCHER_USER entity) — vendor redeems ─────────────

    /**
     * Looks up a VOUCHER_USER by its [voucherCode] and validates it for redemption
     * at [vendorID]. Validates ALL of these against the live database:
     *   1. The code exists
     *   2. It belongs to this vendor's shop
     *   3. It is status=active (not used, not expired)
     *   4. Its expiry date has not passed
     *   5. The customer has enough points at this shop (balance >= pointsRequiredSnapshot)
     *
     * On success, [onValid] is called with the VOUCHER_USER and its reward name.
     * On any failure, [onInvalid] is called with a plain-language reason.
     */
    fun validateVoucherForRedemption(
        voucherCode: String,
        vendorID: String,
        onValid: (voucherUser: VoucherUser, rewardName: String) -> Unit,
        onInvalid: (reason: String) -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d(TAG, "Validating voucherCode=$voucherCode for vendorID=$vendorID")

        // Step 1: find the voucherUser by code
        db.child("voucherUsers")
            .orderByChild("voucherCode")
            .equalTo(voucherCode)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        onInvalid("Voucher code not found. Please check the code and try again.")
                        return
                    }

                    val vu = snapshot.children.firstOrNull()?.getValue(VoucherUser::class.java)
                    if (vu == null) {
                        onInvalid("Voucher code could not be read. Please try again.")
                        return
                    }

                    // Step 2: correct shop?
                    if (vu.vendorID != vendorID) {
                        Log.w(TAG, "Voucher belongs to vendorID=${vu.vendorID}, not $vendorID")
                        onInvalid("This voucher is for a different shop.")
                        return
                    }

                    // Step 3: already used or expired by status?
                    if (vu.voucherStatus == VoucherStatus.USED) {
                        onInvalid("This voucher has already been used.")
                        return
                    }
                    if (vu.voucherStatus == VoucherStatus.EXPIRED) {
                        onInvalid("This voucher has expired.")
                        return
                    }

                    // Step 4: expiry date check
                    val nowMs = System.currentTimeMillis()
                    if (vu.voucherExpiryDate > 0L && vu.voucherExpiryDate < nowMs) {
                        onInvalid("This voucher has expired.")
                        return
                    }

                    // Step 5: check customer balance at this shop
                    checkBalanceAndFetchRuleName(vu, vendorID, onValid, onInvalid, onError)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "validateVoucher cancelled: ${error.message}")
                    onError("Could not verify voucher: ${error.message}")
                }
            })
    }

    private fun checkBalanceAndFetchRuleName(
        vu: VoucherUser,
        vendorID: String,
        onValid: (VoucherUser, String) -> Unit,
        onInvalid: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Fetch all point rows for this customer at this vendor to compute balance
        db.child("points")
            .orderByChild("userID")
            .equalTo(vu.userID)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var balance = 0
                    for (child in snapshot.children) {
                        val p = child.getValue(Point::class.java) ?: continue
                        if (p.vendorID == vendorID) {
                            balance += p.pointEarnedAmount - p.pointRedeemedAmount
                        }
                    }

                    Log.d(TAG, "Customer balance at shop=$balance, required=${vu.pointsRequiredSnapshot}")

                    if (balance < vu.pointsRequiredSnapshot) {
                        onInvalid(
                            "Customer only has $balance points at this shop but this " +
                            "voucher requires ${vu.pointsRequiredSnapshot} points."
                        )
                        return
                    }

                    // Fetch the reward name from the rule
                    db.child("vouchers").child(vu.voucherID)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(ruleSnap: DataSnapshot) {
                                val rule = ruleSnap.getValue(Voucher::class.java)
                                val rewardName = rule?.voucherName ?: "the reward"
                                Log.i(TAG, "Voucher valid — reward: $rewardName")
                                onValid(vu, rewardName)
                            }
                            override fun onCancelled(error: DatabaseError) {
                                // Rule fetch failed but voucher itself is valid — use fallback name
                                onValid(vu, "the reward")
                            }
                        })
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Balance check cancelled: ${error.message}")
                    onError("Could not check customer balance: ${error.message}")
                }
            })
    }

    /**
     * Atomically redeems a voucher:
     *   - Sets voucherUsers/{voucherUserID}/voucherStatus = "used"
     *   - Pushes a new points ledger row with pointRedeemedAmount = pointsRequiredSnapshot
     *
     * Both writes happen in a single updateChildren() call (build guide Section 4).
     * If the network drops, Firebase retries the full batch — never half of it.
     *
     * @param voucherUser  The validated VoucherUser to mark used
     * @param onResult     true = full success, false = write failed
     */
    fun redeemVoucher(
        voucherUser: VoucherUser,
        onResult: (success: Boolean) -> Unit
    ) {
        val pointRef = db.child("points").push()
        val pointID  = pointRef.key ?: return onResult(false)

        val pointRow = Point(
            pointID             = pointID,
            userID              = voucherUser.userID,
            vendorID            = voucherUser.vendorID,
            pointEarnedAmount   = 0,
            pointRedeemedAmount = voucherUser.pointsRequiredSnapshot,
            pointSource         = PointSource.REDEMPTION
        )

        val updates = mapOf<String, Any>(
            "voucherUsers/${voucherUser.voucherUserID}/voucherStatus" to VoucherStatus.USED,
            "points/$pointID" to pointRow
        )

        db.root.updateChildren(updates)
            .addOnSuccessListener {
                Log.i(TAG, "Voucher redeemed: ${voucherUser.voucherUserID}, " +
                           "deducted ${voucherUser.pointsRequiredSnapshot} pts")
                onResult(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "redeemVoucher failed", e)
                onResult(false)
            }
    }
}
