package com.sapotlokal

/**
 * AppConfig — the single source of truth for every tunable number in SapotLokal.
 *
 * WHY THIS EXISTS: The build guide (Rule 8) requires that points per visit, level
 * thresholds, spin ranges, and voucher expiry live in one clearly named place so
 * they can be changed without hunting through screens.
 *
 * HOW TO USE: Import this object anywhere in the app and read its properties.
 * Never hardcode these numbers in an Activity, Fragment, or service.
 *
 * OPEN DECISIONS (from build guide Section 5):
 *   - SPIN_WHEEL_MIN / SPIN_WHEEL_MAX: defaults proposed here; developer must confirm
 *     in Phase 6 before Spin the Wheel is built.
 *   - VOUCHER_EXPIRY_DAYS: developer must confirm in Phase 5 before auto-voucher is built.
 */
object AppConfig {

    // ── Firebase Realtime Database ─────────────────────────────────────────────

    /**
     * Regional database URL for the SapotLokal Firebase project.
     *
     * The database was created in the asia-southeast1 region.
     * Using the default URL (no region) causes the server to forcefully close
     * the connection with "Database lives in a different region".
     *
     * Always obtain the database reference via:
     *   FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference
     * Never call FirebaseDatabase.getInstance() with no argument.
     */
    const val DATABASE_URL =
        "https://sapotlokal-cd37b-default-rtdb.asia-southeast1.firebasedatabase.app"

    // ── Points ────────────────────────────────────────────────────────────────

    /**
     * Points a Customer earns each time they scan a valid Shop QR code.
     * Source: Flowchart says 10; Simple Guide says "e.g. 20". Default = 10 (Flowchart wins).
     * Build guide Open Decision #1.
     */
    const val POINTS_PER_SHOP_VISIT: Int = 10

    /**
     * Minimum points Spin the Wheel can award in a single spin.
     * Build guide Open Decision #2 — AI proposal: 5. Developer must confirm in Phase 6.
     */
    const val SPIN_WHEEL_MIN_POINTS: Int = 5

    /**
     * Maximum points Spin the Wheel can award in a single spin.
     * Build guide Open Decision #2 — AI proposal: 50. Developer must confirm in Phase 6.
     */
    const val SPIN_WHEEL_MAX_POINTS: Int = 50

    /**
     * Points awarded for completing a Memory Match game.
     * Build guide Open Decision #2 — AI proposal: 20. Developer must confirm in Phase 6.
     */
    const val MEMORY_MATCH_REWARD_POINTS: Int = 20

    // ── Shop QR Code ──────────────────────────────────────────────────────────

    /**
     * How often the Shop QR code rotates, in seconds.
     * 60 seconds = TOTP-style rotation. Old screenshots can't be reused.
     */
    const val QR_ROTATION_SECONDS: Long = 60L

    // ── Vouchers ──────────────────────────────────────────────────────────────

    /**
     * How many days a customer voucher (VOUCHER_USER) is valid after it is created.
     * Build guide Open Decision #8 — no source specified. AI will ask the developer
     * to confirm this in Phase 5 before auto-voucher logic is built.
     * Placeholder: 30 days.
     */
    const val VOUCHER_EXPIRY_DAYS: Int = 30

    // ── Levels ────────────────────────────────────────────────────────────────

    /**
     * Lifetime-earned points required to reach each level.
     * Level is computed from EARNED points only — it never goes down when a voucher
     * is redeemed (build guide Open Decision #12).
     * Level 1 = 0 points (everyone starts here).
     *
     * These thresholds are placeholders — update when the Figma designs confirm the
     * level names and required point counts.
     */
    val LEVEL_THRESHOLDS: List<Int> = listOf(
        0,    // Level 1 — Pendatang Baru (Newcomer)
        100,  // Level 2 — Tetamu Setia (Regular)
        300,  // Level 3 — Pelanggan Emas (Gold Customer)
        600,  // Level 4 — Sahabat Lokal (Local Friend)
        1000  // Level 5 — Juara SapotLokal (SapotLokal Champion)
    )

    /**
     * Returns the level (1-indexed) for a given lifetime-earned point total.
     * If lifetimePoints exceeds all thresholds, returns the maximum level.
     */
    fun levelForPoints(lifetimePoints: Int): Int {
        // Count how many thresholds the player has reached.
        // LEVEL_THRESHOLDS[0] = 0 means everyone is at least Level 1.
        // LEVEL_THRESHOLDS[1] = 100 means Level 2 starts at 100, etc.
        var level = 0
        for (threshold in LEVEL_THRESHOLDS) {
            if (lifetimePoints >= threshold) level++
        }
        // level is now in [1..LEVEL_THRESHOLDS.size] because threshold[0]=0 is always met
        return level.coerceAtLeast(1).coerceAtMost(LEVEL_THRESHOLDS.size)
    }

    /**
     * Returns the point threshold to reach the NEXT level, or null if already at max.
     */
    fun nextLevelThreshold(lifetimePoints: Int): Int? {
        val current = levelForPoints(lifetimePoints)
        // current is 1-indexed; LEVEL_THRESHOLDS is 0-indexed.
        // Level N requires LEVEL_THRESHOLDS[N-1] points.
        // Level N+1 requires LEVEL_THRESHOLDS[N] points (index = current).
        return LEVEL_THRESHOLDS.getOrNull(current) // null when at max level
    }

    // ── Spin the Wheel daily limit ─────────────────────────────────────────────

    /**
     * A customer may spin the wheel at most once per calendar day.
     * The check is done against the database (build guide Rule 43 — "every check
     * runs against the live database, never data stored on the phone").
     */
    const val SPIN_WHEEL_DAILY_LIMIT: Int = 1
}
