package com.sapotlokal.data.model

import com.google.firebase.database.PropertyName
import com.sapotlokal.AppConfig

/**
 * Data classes mirroring the SapotLokal ERD entities.
 *
 * These are plain Kotlin data classes — no logic, just structure.
 * Firebase Realtime Database serialises/deserialises these automatically
 * because every field has a no-arg default value.
 *
 * FIELD NAMES match the ERD exactly so the JSON keys in the database
 * are identical to what the build guide specifies.
 *
 * PASSWORDS are not here — Firebase Auth owns them (build guide Section 3.4).
 */

// ── USER ──────────────────────────────────────────────────────────────────────
/**
 * Stored at: /users/{userID}
 * role is either "customer" or "vendor". It is written once at sign-up
 * and the Security Rules prevent it from being changed afterward.
 */
data class User(
    val userID: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val role: String = "",           // "customer" or "vendor"
    val avatar: String = "",         // optional profile avatar id (see AvatarDef); empty = none
    val xp: Int = 0,                 // customers only; never decreases (REFRACTOR.md §4)
    val rank: Int = 1                // 1–5, cached from xp for the leaderboard and display
)

// ── VENDOR ────────────────────────────────────────────────────────────────────
/**
 * Stored at: /vendors/{vendorID}
 * One-to-one with a USER whose role = "vendor".
 * ssmNumber is required at sign-up (build guide Section 2).
 */
data class Vendor(
    val vendorID: String = "",
    val userID: String = "",        // FK → users/{userID}
    val vendorName: String = "",
    val vendorCategory: String = "",
    val vendorAddress: String = "",
    val ssmNumber: String = "",     // Required; stored but never auto-verified
    val vendorDescription: String = "", // Optional merchant description (Phase 6)

    @get:PropertyName("isOpen")
    @set:PropertyName("isOpen")
    var isOpen: Boolean = true,

    val operatingHours: String = "",
    val lastStatusChangedAt: Long = 0L,
    val pointsPerVisit: Int = AppConfig.DEFAULT_POINTS_PER_VISIT  // one of AppConfig.ALLOWED_POINTS_PER_VISIT
)

// ── QRCODE ────────────────────────────────────────────────────────────────────
/**
 * Stored at: /qrcodes/{qrcodeID}
 * Each row represents one time-window code for a vendor's shop QR.
 * The value rotates every AppConfig.QR_ROTATION_SECONDS seconds.
 * Phase 4 writes new rows; expired ones are cleaned up in Phase 7.
 */
data class QrCode(
    val qrcodeID: String = "",
    val vendorID: String = "",          // FK → vendors/{vendorID}
    val qrcodeValue: String = "",       // The secret value encoded in the QR image
    val qrcodeGenerateDate: Long = 0L,  // Unix epoch ms
    val qrcodeExpiryDate: Long = 0L,    // Unix epoch ms

    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = false
)

// ── POINT ─────────────────────────────────────────────────────────────────────
/**
 * Stored at: /points/{pointID}
 * Ledger row — every points change is a separate row.
 * Never overwrite a single total: balances are computed by summing earned minus redeemed.
 *
 * pointSource values (use these constants — see PointSource below):
 *   "shop_visit", "spin_wheel", "memory_match", "challenge"
 *
 * For earned rows: pointEarnedAmount > 0, pointRedeemedAmount = 0.
 * For redemption rows: pointEarnedAmount = 0, pointRedeemedAmount > 0.
 */
data class Point(
    val pointID: String = "",
    val userID: String = "",            // FK → users/{userID}
    val vendorID: String = "",          // FK → vendors/{vendorID}
    val pointEarnedAmount: Int = 0,
    val pointRedeemedAmount: Int = 0,
    val pointSource: String = "",       // See PointSource constants
    val timestamp: Long = 0L,
    val basePoints: Int = 0,            // shop_visit: the shop's points per visit at the time
    val rankBonus: Double = 1.0         // shop_visit: the customer's rank bonus at the time
)


/** Shop points only come from visits and are only spent on rewards (games and missions give XP). */
object PointSource {
    const val SHOP_VISIT    = "shop_visit"
    const val REDEMPTION    = "redemption"  // When a voucher is claimed or redeemed
}

// ── VOUCHER (reward rule) ──────────────────────────────────────────────────────
/**
 * Stored at: /vouchers/{voucherID}
 * Created by the shop owner. NOT a per-customer voucher — this is the rule
 * ("at N points, give this reward"). The build guide calls this a "reward rule"
 * in UI text to avoid confusion.
 *
 * isActive: ERD deviation — field added per build guide Open Decision #11.
 * Turning isActive=false stops new VOUCHER_USER records from being created
 * for this rule, but existing ones remain valid.
 *
 * See docs/erd-changes.md for the full record of this deviation.
 */
data class Voucher(
    val voucherID: String = "",
    val vendorID: String = "",          // FK → vendors/{vendorID}
    val voucherName: String = "",       // e.g. "Free Cake"
    val voucherDescription: String = "",
    val pointsRequired: Int = 0,
    // Without @PropertyName Firebase would store this as "active" (it strips the "is" prefix of a
    // Kotlin Boolean getter), while setRuleActive() writes "isActive" — the two never matched, so
    // switching a reward on/off could not work. Same fix Vendor.isOpen already uses.
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true        // ERD addition — see erd-changes.md
)

// ── VOUCHER_USER (customer voucher) ───────────────────────────────────────────
/**
 * Stored at: /voucherUsers/{voucherUserID}
 * Auto-created by the app when a customer's per-shop balance crosses a reward
 * rule's pointsRequired. One VOUCHER_USER per customer per rule per earn cycle.
 *
 * voucherStatus: "active", "used", or "expired"
 *
 * pointsRequiredSnapshot: ERD addition — proposed per build guide Open Decision #14.
 * Copying the cost from the rule at creation time means editing the rule later
 * can't change what an already-issued voucher costs.
 * See docs/erd-changes.md for the full record of this deviation.
 */
data class VoucherUser(
    val voucherUserID: String = "",
    val userID: String = "",            // FK → users/{userID}
    val voucherID: String = "",         // FK → vouchers/{voucherID} (the rule)
    val vendorID: String = "",          // Denormalised for easy per-shop queries
    val voucherCode: String = "",       // Unique, human-typeable code
    val voucherStatus: String = "active", // "active" | "used" | "expired"
    val voucherExpiryDate: Long = 0L,   // Unix epoch ms
    val pointsRequiredSnapshot: Int = 0, // ERD addition — see erd-changes.md
    val voucherTitle: String = "",       // Snapshot of rule voucherName
    val createdAt: Long = 0L,            // Unix epoch ms
    /** true = the points were spent when the customer claimed it (nothing more is deducted at redeem). */
    val pointsDeducted: Boolean = false,
    /** The points-ledger row that recorded the spend at claim time. */
    val claimPointID: String = "",
    /** When the shop marked it used (epoch ms, server time); 0 for vouchers used before this was kept. */
    val voucherUsedAt: Long = 0L
)

object VoucherStatus {
    const val ACTIVE  = "active"
    const val USED    = "used"
    const val EXPIRED = "expired"
}

// ── GAME ──────────────────────────────────────────────────────────────────────
/**
 * Stored at: /games/{gameID}
 * Records one game play by a user (Spin the Wheel or Memory Match). Games pay XP, never shop points.
 */
data class Game(
    val gameID: String = "",
    val userID: String = "",            // FK → users/{userID}
    val gameType: String = "",          // GameType.SPIN_WHEEL or GameType.MEMORY_MATCH
    val gameAttempt: Int = 0,
    val gameXpRewarded: Int = 0,        // XP this play paid (0 once the daily XP wins are used)
    val gameDay: String = "",           // "yyyy-MM-dd" in AppConfig.GAMES_RESET_TIME_ZONE
    val gamePlayedAt: Long = 0L         // epoch ms
)

object GameType {
    const val SPIN_WHEEL   = "spin_wheel"
    const val MEMORY_MATCH = "memory_match"
}


/** Fields written when an account is created; leaves out the optional avatar so it can be validated later. */
fun User.toSignUpMap(): Map<String, Any> = mapOf(
    "userID" to userID,
    "userName" to userName,
    "userEmail" to userEmail,
    "role" to role
)
