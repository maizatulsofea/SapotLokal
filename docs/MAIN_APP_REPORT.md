# SapotLokal — Main App Report

_Generated: 2026-10-21. Read-only audit of the codebase against SAPOTLOKAL_BUILD_GUIDE.md._

---

## 1. Project Status

### Phase progress

| Phase | Title | Status | Evidence |
|-------|-------|--------|----------|
| 1 | Foundations & Data Model | **Done** | Firebase connected, security rules written to `docs/firebase-rules.json`, all nine database nodes defined in `docs/database-structure.md`, `AppConfig.kt` holds all tunable numbers, `SplashActivity` + package skeleton exist |
| 2 | Accounts & Role-Based Sign-Up | **Done** | `RolePickerActivity`, `SignUpCustomerActivity`, `SignUpVendorActivity`, `LoginActivity` all exist with full validation; role stored and locked by Security Rules |
| 3 | Customer Side, Core Loop | **Done** | `CustomerHomeActivity` (live points listener, level + progress bar), `FindShopsActivity`, `ScanActivity` (ML Kit camera + debug panel), `PointRepository.recordShopVisit()` writes ledger rows |
| 4 | Shop Owner Side, Core Loop | **Done** | `VendorHomeActivity`, `ShowQrActivity` (60-second rotating QR via `QrCodeRepository.publishNewCode()`), `RewardRulesActivity` + `AddEditRuleActivity`, `ScanToRedeemActivity` (camera + manual entry + atomic redemption) |
| 5 | Rewards & Vouchers | **Not started** | `VoucherRepository` has `validateVoucherForRedemption()` and `redeemVoucher()` written (the vendor redemption side works), but there is no auto-voucher creation logic, no `MyVouchersActivity`, and no `VOUCHER_USER` write path from the customer side. `CustomerHomeActivity` has a `btnMyVouchers` that shows a Toast saying "coming in Phase 5". |
| 6 | Games, Levels & Challenges | **Not started** | No `SpinWheelActivity`, `MemoryMatchActivity`, or Challenges screen. `SapotFcmService` is a stub. Level display on Home is built (uses `AppConfig.levelForPoints`), but there is no level-up animation/moment. `GAME` and `CHALLENGE` data classes and Firebase nodes are defined and ready. |
| 7 | Polish & Testing | **Not started** | Debug helpers are still present in `ScanActivity` and `ShowQrActivity`. Screens have not been compared to Figma. |

### Do these work today?

| Feature | Works? | Notes |
|---------|--------|-------|
| Sign-up (Customer) | **Yes** | `SignUpCustomerActivity` validates and writes USER record |
| Sign-up (Shop Owner) | **Yes** | `SignUpVendorActivity` validates (SSM required), writes USER then VENDOR sequentially |
| Login | **Yes** | `LoginActivity` reads role from DB and routes to correct home |
| Role routing | **Yes** | `SplashActivity` and `LoginActivity` both read `/users/{uid}/role` from live DB |
| Customer Home | **Yes** | Shows lifetime points, level chip, progress bar; updates live via `PointRepository.listenToPointsForUser()` |
| POINT ledger | **Yes** | `PointRepository.recordShopVisit()` writes ledger rows; balance computed by summing rows |
| Shop scan (QR) | **Yes** | `ScanActivity` uses ML Kit + CameraX; validates against `/qrcodes` in DB; writes point row on success |
| Config class (`AppConfig`) | **Yes** | `app/src/main/kotlin/com/sapotlokal/AppConfig.kt` — holds all tunable numbers as constants/functions |

---

## 2. Build Setup

### Key values

| Item | Value |
|------|-------|
| Package name / applicationId | `com.sapotlokal` |
| minSdk | 26 (Android 8.0) |
| targetSdk | 37 |
| compileSdk | 37 |
| jvmTarget / sourceCompatibility | `JavaVersion.VERSION_21` (JDK 21) |
| AGP version | `9.4.0` |
| Kotlin | Built into AGP 9.x (no separate Kotlin plugin declared) |
| Gradle | `9.6.0` (in `gradle-wrapper.properties`) |

### Full contents of `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    // AGP 9.x has built-in Kotlin support — no separate kotlin("android") plugin needed.
    // If you need to opt out for any reason, add android.builtInKotlin=false to gradle.properties.
    id("com.google.gms.google-services")  // Firebase Google Services plugin
}

android {
    namespace = "com.sapotlokal"
    compileSdk = 37  // Latest API level supported by AGP 9.4.0

    defaultConfig {
        applicationId = "com.sapotlokal"
        minSdk = 26           // Android 8.0 — as required by the build guide
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // JDK 21 matches Android Studio's bundled JDK.
        // AGP 9.x minimum is JDK 17 — 21 is fully compatible and is what ships with Android Studio.
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Kotlin source sets — AGP 9.x auto-configures Kotlin; this just points to the right folder.
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/kotlin")
        }
    }

    buildFeatures {
        viewBinding = true   // Replaces findViewById — safer and cleaner for XML layouts
    }
}

dependencies {
    // ── AndroidX ─────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)

    // ── Material Design ───────────────────────────────────────────────────────
    implementation(libs.material)

    // ── Firebase (BoM manages all Firebase library versions) ──────────────────
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.messaging)

    // ── QR Scanning — ML Kit + CameraX ────────────────────────────────────────
    // ML Kit barcode-scanning does the recognition; CameraX feeds it the frames.
    // CameraX handles camera lifecycle, rotation, and permission automatically.
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)       // PreviewView widget used in the scan layout

    // ── QR Generation — ZXing core ────────────────────────────────────────────
    // Used ONLY to draw QR Bitmaps into an ImageView. No camera access.
    // The deprecated zxing-android-embedded scanner is NOT included.
    implementation(libs.zxing.core)

    // ── Testing ───────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
```

### Tests, minify, and branch

- **Tests exist:** Yes — `app/src/test/kotlin/com/sapotlokal/` and `app/src/androidTest/kotlin/com/sapotlokal/` folders are present but contain no test files yet (the directories are empty).
- **minify/R8:** `isMinifyEnabled = false` in the release build type. R8 is off.
- **Current git branch:** `main`

---

## 3. Config Class (Build Guide Rule 8)

### Path

`app/src/main/kotlin/com/sapotlokal/AppConfig.kt`

### Full contents

```kotlin
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
```

### Which classes read it and how

| Class | What it reads | How |
|-------|--------------|-----|
| `PointRepository` | `DATABASE_URL`, `POINTS_PER_SHOP_VISIT` | `AppConfig.DATABASE_URL` for DB init; `AppConfig.POINTS_PER_SHOP_VISIT` in `recordShopVisit()` |
| `QrCodeRepository` | `DATABASE_URL`, `QR_ROTATION_SECONDS` | DB URL in object init; rotation in `publishNewCode()` |
| `VendorRepository` | `DATABASE_URL` | DB URL in object init |
| `VoucherRepository` | `DATABASE_URL` | DB URL in object init |
| `CustomerHomeActivity` | `levelForPoints()`, `nextLevelThreshold()`, `LEVEL_THRESHOLDS` | Called in `updateUi()` after points update |
| `ScanActivity` | `DATABASE_URL`, `POINTS_PER_SHOP_VISIT` | DB URL indirectly via repository; points count in `showSuccessOverlay()` |
| `ShowQrActivity` | `QR_ROTATION_SECONDS` | CountDownTimer duration |
| `LoginActivity`, `SignUpCustomerActivity`, `SignUpVendorActivity`, `SplashActivity` | `DATABASE_URL` | Direct `FirebaseDatabase.getInstance(AppConfig.DATABASE_URL)` |

---

## 4. Points and the Database

### Real node names, field names, and types

#### `/users/{userID}`

| Field | Type | Notes |
|-------|------|-------|
| `userID` | String | Firebase Auth UID |
| `userName` | String | Display name |
| `userEmail` | String | Auth email |
| `role` | String | `"customer"` or `"vendor"` |

#### `/vendors/{vendorID}`

| Field | Type | Notes |
|-------|------|-------|
| `vendorID` | String | Firebase push ID (set to uid for vendors) |
| `userID` | String | FK → `/users/{userID}` |
| `vendorName` | String | Shop name |
| `vendorCategory` | String | e.g. `"Food & Beverage"` |
| `vendorAddress` | String | Free-text address |
| `ssmNumber` | String | Required; never auto-verified |

#### `/points/{pointID}`

| Field | Type | Notes |
|-------|------|-------|
| `pointID` | String | Firebase push ID |
| `userID` | String | FK → `/users/{userID}` |
| `vendorID` | String | FK → `/vendors/{vendorID}` |
| `pointEarnedAmount` | Int | > 0 for earn rows; 0 for redemption rows |
| `pointRedeemedAmount` | Int | > 0 for redemption rows; 0 for earn rows |
| `pointSource` | String | See pointSource values below |

#### `/games/{gameID}`

| Field | Type | Notes |
|-------|------|-------|
| `gameID` | String | Firebase push ID |
| `userID` | String | FK → `/users/{userID}` |
| `gameType` | String | `"spin_wheel"` or `"memory_match"` |
| `gameAttempt` | Int | Cumulative count for this game type |
| `gamePointsRewarded` | Int | Points awarded in this play |

#### `/vouchers/{voucherID}` (reward rules)

| Field | Type | Notes |
|-------|------|-------|
| `voucherID` | String | Firebase push ID |
| `vendorID` | String | FK → `/vendors/{vendorID}` |
| `voucherName` | String | e.g. `"Free Cake"` |
| `voucherDescription` | String | Longer description |
| `pointsRequired` | Int | Threshold to unlock |
| `isActive` | Boolean | ERD addition — see `erd-changes.md` Change 1 |

#### `/voucherUsers/{voucherUserID}` (customer vouchers)

| Field | Type | Notes |
|-------|------|-------|
| `voucherUserID` | String | Firebase push ID |
| `userID` | String | FK → `/users/{userID}` |
| `voucherID` | String | FK → `/vouchers/{voucherID}` |
| `vendorID` | String | Denormalised FK for per-shop queries |
| `voucherCode` | String | Unique, human-typeable code |
| `voucherStatus` | String | `"active"` / `"used"` / `"expired"` |
| `voucherExpiryDate` | Long | Unix epoch ms |
| `pointsRequiredSnapshot` | Int | ERD addition — see `erd-changes.md` Change 2 |

#### `/challenges/{challengeID}`

| Field | Type | Notes |
|-------|------|-------|
| `challengeID` | String | Firebase push ID |
| `challengeTitle` | String | e.g. `"Visit 5 shops this week"` |
| `challengeDescription` | String | Longer explanation |
| `challengeTargetValue` | Int | e.g. `5` |
| `challengeRewardPoint` | Int | Points awarded on completion |
| `isActive` | Boolean | `false` = hidden from users |

#### `/userChallenges/{userChallengeID}`

| Field | Type | Notes |
|-------|------|-------|
| `userChallengeID` | String | Firebase push ID |
| `userID` | String | FK → `/users/{userID}` |
| `challengeID` | String | FK → `/challenges/{challengeID}` |
| `currentValue` | Int | User's current progress |
| `userChallengeStatus` | String | `"in_progress"` or `"completed"` |

---

### Full contents of the Firebase rules file

File: `docs/firebase-rules.json`

```json
{
  "rules": {
    "users": {
      ".read": "auth != null",
      "$userID": {
        ".write": "auth != null && auth.uid === $userID",
        "role": {
          ".validate": "newData.isString() && (newData.val() === 'customer' || newData.val() === 'vendor') && (!data.exists() || data.val() === newData.val())"
        }
      }
    },
    "vendors": {
      ".read": "auth != null",
      "$vendorID": {
        ".write": "auth != null && root.child('users/' + auth.uid + '/role').val() === 'vendor' && (newData.child('userID').val() === auth.uid)",
        ".indexOn": ["userID"]
      }
    },
    "qrcodes": {
      ".read": "auth != null",
      "$qrcodeID": {
        ".write": "auth != null && root.child('users/' + auth.uid + '/role').val() === 'vendor' && root.child('vendors/' + newData.child('vendorID').val() + '/userID').val() === auth.uid",
        ".indexOn": ["vendorID", "isActive", "qrcodeValue"]
      }
    },
    "points": {
      "$pointID": {
        ".read": "auth != null && data.child('userID').val() === auth.uid",
        ".write": "auth != null && newData.child('userID').val() === auth.uid",
        ".validate": "!data.exists() || newData.val() === data.val()",
        ".indexOn": ["userID", "vendorID"]
      }
    },
    "vouchers": {
      ".read": "auth != null",
      "$voucherID": {
        ".write": "auth != null && root.child('users/' + auth.uid + '/role').val() === 'vendor' && root.child('vendors/' + newData.child('vendorID').val() + '/userID').val() === auth.uid",
        ".indexOn": ["vendorID", "isActive"]
      }
    },
    "voucherUsers": {
      "$voucherUserID": {
        ".read": "auth != null && (data.child('userID').val() === auth.uid || root.child('vendors/' + data.child('vendorID').val() + '/userID').val() === auth.uid)",
        ".write": "auth != null && ((!data.exists() && newData.child('userID').val() === auth.uid && newData.child('voucherStatus').val() === 'active') || (data.exists() && root.child('vendors/' + data.child('vendorID').val() + '/userID').val() === auth.uid))",
        ".indexOn": ["userID", "vendorID", "voucherStatus"],
        "voucherStatus": {
          ".validate": "!(data.val() === 'used' && newData.val() !== 'used')"
        }
      }
    },
    "games": {
      "$gameID": {
        ".read": "auth != null && data.child('userID').val() === auth.uid",
        ".write": "auth != null && newData.child('userID').val() === auth.uid",
        ".indexOn": ["userID", "gameType"]
      }
    },
    "challenges": {
      ".read": "auth != null",
      "$challengeID": {
        ".write": "auth != null"
      }
    },
    "userChallenges": {
      "$userChallengeID": {
        ".read": "auth != null && data.child('userID').val() === auth.uid",
        ".write": "auth != null && newData.child('userID').val() === auth.uid",
        ".indexOn": ["userID"]
      }
    }
  }
}
```

---

### Classes that write to or read from `points` and `games`

**`/points` node:**

| Class | Operation | Method / location |
|-------|-----------|-------------------|
| `PointRepository` | Write (earn) | `recordShopVisit()` — pushes a new row with `pointEarnedAmount = AppConfig.POINTS_PER_SHOP_VISIT`, `pointSource = "shop_visit"` |
| `PointRepository` | Read (live) | `listenToPointsForUser()` — live listener used by `CustomerHomeActivity` |
| `PointRepository` | Read (one-shot) | `fetchAllPointsForUser()` |
| `VoucherRepository` | Write (redemption) | `redeemVoucher()` — pushes a new row with `pointRedeemedAmount = pointsRequiredSnapshot`, `pointSource = "redemption"` |
| `VoucherRepository` | Read (balance check) | `checkBalanceAndFetchRuleName()` — reads all user's point rows to compute per-shop balance before redemption |

**`/games` node:**

No class currently reads from or writes to the `/games` node. The `Game` data class and the node exist in the data model and Security Rules, but there is no `GamesRepository` or game activity yet.

---

### Exact code for key operations

#### Writing a POINT row (shop visit)
File: `app/src/main/kotlin/com/sapotlokal/data/repository/PointRepository.kt`, lines 39–58

```kotlin
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
```

#### Calculating per-shop balance
File: `app/src/main/kotlin/com/sapotlokal/data/repository/VoucherRepository.kt`, lines 244–251

```kotlin
var balance = 0
for (child in snapshot.children) {
    val p = child.getValue(Point::class.java) ?: continue
    if (p.vendorID == vendorID) {
        balance += p.pointEarnedAmount - p.pointRedeemedAmount
    }
}
```

Also in `PointRepository` (lines 87–89):
```kotlin
perShop[p.vendorID] =
    current + p.pointEarnedAmount - p.pointRedeemedAmount
```

#### Calculating lifetime points and level
File: `app/src/main/kotlin/com/sapotlokal/data/repository/PointRepository.kt`, lines 84–93

```kotlin
for (child in snapshot.children) {
    val p = child.getValue(Point::class.java) ?: continue
    lifetimeEarned += p.pointEarnedAmount   // earned only, never reduced
    val current = perShop.getOrDefault(p.vendorID, 0)
    perShop[p.vendorID] =
        current + p.pointEarnedAmount - p.pointRedeemedAmount
}
```

Level is then derived in `AppConfig.levelForPoints(lifetimeEarned)`:

```kotlin
fun levelForPoints(lifetimePoints: Int): Int {
    var level = 0
    for (threshold in LEVEL_THRESHOLDS) {
        if (lifetimePoints >= threshold) level++
    }
    return level.coerceAtLeast(1).coerceAtMost(LEVEL_THRESHOLDS.size)
}
```

#### Refreshing Home after points change
File: `app/src/main/kotlin/com/sapotlokal/ui/customer/CustomerHomeActivity.kt`, lines 81–86 and 144–152

```kotlin
private fun startListeningToPoints(userID: String) {
    pointsListener = PointRepository.listenToPointsForUser(
        userID = userID,
        onUpdate = { _, lifetimeEarned -> updateUi(lifetimeEarned) },
        onError  = { /* keep showing last values */ }
    )
}

// Listener is detached in onStop() and re-attached in onStart():
override fun onStop() {
    super.onStop()
    val uid = auth.currentUser?.uid ?: return
    pointsListener?.let { PointRepository.removePointsListener(uid, it) }
    pointsListener = null
}

override fun onStart() {
    super.onStart()
    val uid = auth.currentUser?.uid ?: return
    if (pointsListener == null) {
        startListeningToPoints(uid)
    }
}
```

The Firebase live listener fires immediately when `CustomerHomeActivity` returns from `ScanActivity`; no manual refresh is needed.

---

### All `pointSource` values used so far

Defined in `app/src/main/kotlin/com/sapotlokal/data/model/Models.kt` (lines 86–92):

```kotlin
object PointSource {
    const val SHOP_VISIT    = "shop_visit"
    const val SPIN_WHEEL    = "spin_wheel"
    const val MEMORY_MATCH  = "memory_match"
    const val CHALLENGE     = "challenge"
    const val REDEMPTION    = "redemption"
}
```

Only `"shop_visit"` and `"redemption"` are actually written to the database today. `"spin_wheel"`, `"memory_match"`, and `"challenge"` are constants ready for Phase 6 but not yet used.

---

### How the signed-in user ID and role are obtained

**User ID (`uid`):**  
Every Activity that needs the UID calls `FirebaseAuth.getInstance().currentUser?.uid`.  
Example from `ScanActivity` (line 215):
```kotlin
val userID = auth.currentUser?.uid ?: run {
    showError("Not logged in. Please restart the app.")
    isProcessing = false
    return
}
```

**Role:**  
Role is NOT stored on the device. It is read from the live database at `/users/{uid}/role` each time it is needed.  
Example from `LoginActivity` (lines 112–129):
```kotlin
private fun fetchRoleAndNavigate(uid: String) {
    db.child("users/$uid/role").get()
        .addOnSuccessListener { snapshot ->
            when (val role = snapshot.getValue(String::class.java)) {
                "customer" -> navigateTo(CustomerHomeActivity::class.java)
                "vendor"   -> navigateTo(VendorHomeActivity::class.java)
                else       -> { auth.signOut(); binding.tilEmail.error = ... }
            }
        }
}
```

---

### `setPersistenceEnabled` and Firebase initialisation

- `setPersistenceEnabled(true)` is **NOT** used anywhere in the codebase. No offline disk cache is enabled.
- Firebase is **not** initialised in a custom `Application` class — there is no `Application` subclass in the project. Firebase is initialised automatically by the `google-services.json` plugin at app start.
- Every `FirebaseDatabase` reference is obtained via `FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference`, specifying the regional URL every time. This is consistent across all files.

---

### Existing code, nodes, or stubs for Spin the Wheel, Memory Match, or the GAME entity

**Data class — exists:**  
`app/src/main/kotlin/com/sapotlokal/data/model/Models.kt`, lines 151–157:

```kotlin
data class Game(
    val gameID: String = "",
    val userID: String = "",
    val gameType: String = "",   // "spin_wheel" or "memory_match"
    val gameAttempt: Int = 0,
    val gamePointsRewarded: Int = 0
)
```

**Firebase node — defined in rules and docs:**  
`/games` node exists in `docs/firebase-rules.json` with read/write rules and `.indexOn ["userID", "gameType"]`.

**Config constants — exist in `AppConfig.kt`:**
```kotlin
const val SPIN_WHEEL_MIN_POINTS: Int = 5
const val SPIN_WHEEL_MAX_POINTS: Int = 50
const val MEMORY_MATCH_REWARD_POINTS: Int = 20
const val SPIN_WHEEL_DAILY_LIMIT: Int = 1
```

**PointSource constants — exist:**
```kotlin
const val SPIN_WHEEL   = "spin_wheel"
const val MEMORY_MATCH = "memory_match"
```

**Activities — NOT FOUND:**  
No `SpinWheelActivity`, `MemoryMatchActivity`, `WheelView`, `GameConfig`, or `GamesRepository` class exists. No layout files for these screens exist.

---

## 5. Navigation and UI

### How screens connect

The app uses **many Activities** — there is no single Activity with Fragments, no Navigation Component, and no bottom navigation bar. Each screen is a separate `Activity`. Navigation is done with `startActivity(Intent(...))`.

**Customer role screens:**
- `SplashActivity` → `LoginActivity` (or directly to home if already logged in)
- `LoginActivity` → `CustomerHomeActivity` (role = "customer")
- `CustomerHomeActivity` → `FindShopsActivity`, `ScanActivity`, My Vouchers (stub toast)
- `ScanActivity` → finishes after scan, returns to `CustomerHomeActivity`

**Shop Owner role screens:**
- `LoginActivity` → `VendorHomeActivity` (role = "vendor")
- `VendorHomeActivity` → `ShowQrActivity`, `RewardRulesActivity`, `ScanToRedeemActivity`
- `RewardRulesActivity` → `AddEditRuleActivity`

The two roles are separated by the login routing: after auth, the app reads `/users/{uid}/role` from Firebase and launches either `CustomerHomeActivity` or `VendorHomeActivity`. The back stack is cleared on login so a customer cannot press Back to reach a vendor screen.

---

### Customer Home layout and class

**Layout file:** `app/src/main/res/layout/activity_customer_home.xml`

Full contents:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
    activity_customer_home.xml — Phase 3 Customer Home Screen
    Shows: greeting, level chip, lifetime points, progress bar to next level,
    and a bottom navigation bar with Home / Find Shops / Scan / My Vouchers.
-->
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/colorBackground">

    <!-- Top AppBar -->
    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/appBarLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:elevation="0dp"
        app:elevation="0dp">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:background="@color/colorPrimary"
            app:title="SapotLokal"
            app:titleTextColor="@color/colorOnPrimary"
            app:menu="@menu/menu_customer_home" />

    </com.google.android.material.appbar.AppBarLayout>

    <!-- Scrollable content -->
    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="24dp">

            <!-- Greeting -->
            <TextView
                android:id="@+id/tvGreeting"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textSize="20sp"
                android:textColor="@color/colorOnBackground"
                android:fontFamily="sans-serif-medium"
                tools:text="Hello, Ahmad!" />

            <View android:layout_width="0dp" android:layout_height="16dp" />

            <!-- Points Card -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                app:cardCornerRadius="16dp"
                app:cardElevation="2dp"
                app:cardBackgroundColor="@color/colorPrimary">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="24dp">

                    <!-- Level chip -->
                    <com.google.android.material.chip.Chip
                        android:id="@+id/chipLevel"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Level 1"
                        android:textColor="@color/colorPrimary"
                        app:chipBackgroundColor="@color/colorOnPrimary"
                        style="@style/Widget.MaterialComponents.Chip.Filter" />

                    <View android:layout_width="0dp" android:layout_height="12dp" />

                    <!-- Total lifetime points label -->
                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Lifetime Points"
                        android:textColor="#CCFFFFFF"
                        android:textSize="13sp" />

                    <!-- Big points number -->
                    <TextView
                        android:id="@+id/tvLifetimePoints"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:textSize="48sp"
                        android:textColor="@color/colorOnPrimary"
                        android:fontFamily="sans-serif-medium"
                        tools:text="340" />

                    <View android:layout_width="0dp" android:layout_height="16dp" />

                    <!-- Progress label row -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal">

                        <TextView
                            android:id="@+id/tvProgressLabel"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:textColor="#CCFFFFFF"
                            android:textSize="12sp"
                            tools:text="Progress to Level 4" />

                        <TextView
                            android:id="@+id/tvProgressCount"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:textColor="#CCFFFFFF"
                            android:textSize="12sp"
                            tools:text="340 / 600 pts" />

                    </LinearLayout>

                    <View android:layout_width="0dp" android:layout_height="6dp" />

                    <!-- Progress bar -->
                    <ProgressBar
                        android:id="@+id/progressLevel"
                        style="?android:attr/progressBarStyleHorizontal"
                        android:layout_width="match_parent"
                        android:layout_height="8dp"
                        android:max="100"
                        android:progressTint="@color/colorOnPrimary"
                        android:progressBackgroundTint="#44FFFFFF"
                        android:progress="0" />

                    <!-- Max level label (hidden until max) -->
                    <TextView
                        android:id="@+id/tvMaxLevel"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="🏆 You've reached the top level!"
                        android:textColor="#CCFFFFFF"
                        android:textSize="13sp"
                        android:visibility="gone" />

                </LinearLayout>

            </com.google.android.material.card.MaterialCardView>

            <View android:layout_width="0dp" android:layout_height="24dp" />

            <!-- Quick action buttons -->
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Quick Actions"
                android:textSize="16sp"
                android:fontFamily="sans-serif-medium"
                android:textColor="@color/colorOnBackground"
                android:layout_marginBottom="12dp" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:weightSum="2">

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnFindShops"
                    android:layout_width="0dp"
                    android:layout_height="56dp"
                    android:layout_weight="1"
                    android:layout_marginEnd="8dp"
                    android:text="Find Shops"
                    app:icon="@android:drawable/ic_menu_mapmode" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnScan"
                    android:layout_width="0dp"
                    android:layout_height="56dp"
                    android:layout_weight="1"
                    android:layout_marginStart="8dp"
                    android:text="Scan QR"
                    app:icon="@android:drawable/ic_menu_camera" />

            </LinearLayout>

            <View android:layout_width="0dp" android:layout_height="16dp" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnMyVouchers"
                android:layout_width="match_parent"
                android:layout_height="56dp"
                android:text="My Vouchers"
                app:icon="@android:drawable/ic_menu_agenda"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton" />

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

**Bottom navigation / menu:**  
There is no `BottomNavigationView`. The Customer Home uses a `MaterialToolbar` at the top with a single overflow menu item (Log Out) defined in `app/src/main/res/menu/menu_customer_home.xml`. Navigation to other screens (Find Shops, Scan, My Vouchers) is done via three `MaterialButton` widgets in the body of the layout.

---

### Suggestion for where a "Games" entry fits

**Best place: add a fourth button below "My Vouchers" on `activity_customer_home.xml`, or convert the three current quick-action buttons to a 2×2 grid that includes "Games".**

Reason: the current Home already has a "Quick Actions" section with buttons. A "Games" button there is consistent with the existing pattern. A `BottomNavigationView` would also work and is common, but it would require restructuring all four customer screens into a single host Activity — a larger change. Adding a button is the smallest and safest addition.

The Games button would launch a new `GamesMenuActivity` (or directly `SpinWheelActivity` / `MemoryMatchActivity` depending on the design). The `Game` data class and Firebase node are already ready.

---

### themes.xml, colors.xml, and styles

**Full contents of `app/src/main/res/values/themes.xml`:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!--
        Base app theme.
        Parent = Material Design theme, light variant (no dark mode yet).
        In Phase 7 we'll align all colours with the Figma export.
    -->
    <style name="Theme.SapotLokal" parent="Theme.MaterialComponents.Light.NoActionBar">
        <item name="colorPrimary">@color/colorPrimary</item>
        <item name="colorPrimaryVariant">@color/colorPrimaryVariant</item>
        <item name="colorOnPrimary">@color/colorOnPrimary</item>
        <item name="colorSecondary">@color/colorSecondary</item>
        <item name="colorSecondaryVariant">@color/colorSecondaryVariant</item>
        <item name="colorOnSecondary">@color/colorOnSecondary</item>
        <item name="colorError">@color/colorError</item>
        <item name="colorOnError">@color/colorOnError</item>
        <item name="android:colorBackground">@color/colorBackground</item>
        <item name="colorSurface">@color/colorSurface</item>
        <item name="colorOnBackground">@color/colorOnBackground</item>
        <item name="colorOnSurface">@color/colorOnSurface</item>
        <item name="colorOutline">@android:color/darker_gray</item>
        <!-- Rounded corners on all Material buttons — matches Figma style -->
        <item name="materialButtonStyle">@style/Widget.SapotLokal.Button</item>
    </style>

    <!-- Rounded button style — apply globally so every screen is consistent -->
    <style name="Widget.SapotLokal.Button" parent="Widget.MaterialComponents.Button">
        <item name="cornerRadius">24dp</item>
    </style>

    <!-- Splash theme — full-screen orange background, no status bar chrome -->
    <style name="Theme.SapotLokal.Splash" parent="Theme.SapotLokal">
        <item name="android:windowBackground">@color/splashBackground</item>
        <item name="android:windowFullscreen">true</item>
        <item name="android:statusBarColor">@color/splashBackground</item>
    </style>
</resources>
```

**Full contents of `app/src/main/res/values/colors.xml`:**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- ── Primary brand colours ────────────────────────────────────────────── -->
    <!-- These are placeholders — update to match your Figma design in Phase 7 -->
    <color name="colorPrimary">#FF6B2C</color>       <!-- Warm orange — energetic, local-market feel -->
    <color name="colorPrimaryVariant">#CC4E14</color>
    <color name="colorOnPrimary">#FFFFFF</color>

    <color name="colorSecondary">#FFD166</color>      <!-- Golden yellow — rewards, positivity -->
    <color name="colorSecondaryVariant">#E0A800</color>
    <color name="colorOnSecondary">#000000</color>

    <color name="colorBackground">#FAFAFA</color>
    <color name="colorSurface">#FFFFFF</color>
    <color name="colorError">#B00020</color>
    <color name="colorOnBackground">#212121</color>
    <color name="colorOnSurface">#212121</color>
    <color name="colorOnError">#FFFFFF</color>

    <!-- Splash background -->
    <color name="splashBackground">#FF6B2C</color>
</resources>
```

**values-night folder:** NOT FOUND. No dark mode colors or themes exist.

**Separate dimens.xml or styles.xml:** NOT FOUND. No `app/src/main/res/values/dimens.xml` or `app/src/main/res/values/styles.xml` exist. All styles are in `themes.xml`.

**Main button, card, and text styles in use:**

| Element | Style |
|---------|-------|
| All `MaterialButton` widgets | `Widget.SapotLokal.Button` (24dp corner radius, global via theme) |
| Outlined button (My Vouchers) | `@style/Widget.MaterialComponents.Button.OutlinedButton` |
| Level chip | `@style/Widget.MaterialComponents.Chip.Filter` |
| Cards (Points Card) | `MaterialCardView` with `app:cardCornerRadius="16dp"`, `app:cardElevation="2dp"` |
| Text (greeting) | `android:textSize="20sp"`, `android:fontFamily="sans-serif-medium"` |
| Text (big points number) | `android:textSize="48sp"`, `android:fontFamily="sans-serif-medium"` |

---

### How the level-up moment is shown

There is **no level-up animation or "level-up moment"** built yet. The Customer Home progress bar and chip update quietly when the live listener fires. Build guide Phase 6 lists "progress bar fill and a level-up moment when a threshold is crossed" as something to build. The code in `CustomerHomeActivity.updateUi()` just sets the progress bar and label — there is no animation, dialog, or celebration effect.

---

### Loading, error, and "no internet" handling

| Screen | Loading | Error | No internet |
|--------|---------|-------|-------------|
| `LoginActivity` | `btnLogin.isEnabled = false` + `progressBar.VISIBLE` | Inline field errors on `TextInputLayout` | `error_no_internet` string shown as field error |
| `SignUpVendorActivity` | `btnSignUp.isEnabled = false` + `progressBar.VISIBLE` | Inline field errors | Generic error shown |
| `ScanActivity` | No explicit loading indicator | `tvError` text overlay, auto-hides after 3 s | `isNetworkAvailable()` check before DB call; shows `error_no_internet` string |
| `ScanToRedeemActivity` | `progressBar.VISIBLE` + `btnSubmitCode.isEnabled = false` | `Toast.makeText(...)` (no dedicated error view) | `isNetworkAvailable()` check; toast with message |
| `CustomerHomeActivity` | No loading indicator; live listener starts immediately | Silent (error from PointRepository is swallowed) | NOT handled — points listener error is ignored |
| `VendorHomeActivity` | No loading indicator | Inline text `binding.tvShopName.text = "Could not load shop: $err"` | NOT explicitly handled |
| `FindShopsActivity` | NOT reviewed in detail (class not read) | Likely Toast | Likely Toast |

The string `error_no_internet` is defined in `strings.xml`: `"No internet connection. Please check your network and try again."`

---

### Language and orientation

- **Language:** The app has strings in both English (`strings.xml`) and mixed Malay — the tagline is `"Sokong tempatan, kutip ganjaran"` (Malay), level names are Malay (`"Pendatang Baru"`, `"Tetamu Setia"`, etc.), and the SSM help text is English. There is **only one `strings.xml`** file; no `values-ms/` or other locale folder exists. The app is not formally translated.
- **Portrait lock:** NOT FOUND. No `android:screenOrientation` attribute appears in `AndroidManifest.xml`. Screens are not locked to portrait.

---

## 6. Manifest

File: `app/src/main/AndroidManifest.xml`

Full contents:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- ── Permissions ──────────────────────────────────────────────────────── -->

    <!-- Internet: required for all Firebase operations (Auth, Realtime Database, FCM) -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Network state: lets the app detect offline mode and show a clear error (Phase 3+) -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <!-- Camera: required for QR scanning via ML Kit (Phase 3 & 4).
         Declared here but the runtime permission request is done in the Scan screen. -->
    <uses-permission android:name="android.permission.CAMERA" />

    <!-- POST_NOTIFICATIONS: required on Android 13+ (API 33+) to show FCM push notifications.
         The runtime permission request is done in the Notification setup (Phase 6). -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Camera is used but not strictly required — app still works without it
         (manual voucher code entry is the fallback per the build guide). -->
    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.SapotLokal">

        <!-- ── Activities ────────────────────────────────────────────────────── -->

        <!-- SplashActivity: the entry point. Shows logo + loading animation,
             then checks auth state and routes to Login or the correct Home screen. -->
        <activity
            android:name=".ui.splash.SplashActivity"
            android:exported="true"
            android:theme="@style/Theme.SapotLokal.Splash">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Placeholder activities for Phase 1 navigation skeleton -->
        <activity android:name=".ui.auth.LoginActivity"            android:exported="false" />
        <activity android:name=".ui.customer.CustomerHomeActivity"  android:exported="false" />
        <activity android:name=".ui.vendor.VendorHomeActivity"      android:exported="false" />

        <!-- Phase 2 auth activities -->
        <activity android:name=".ui.auth.RolePickerActivity"       android:exported="false" />
        <activity android:name=".ui.auth.SignUpCustomerActivity"    android:exported="false" />
        <activity android:name=".ui.auth.SignUpVendorActivity"      android:exported="false" />

        <!-- Phase 3 customer activities -->
        <activity android:name=".ui.customer.FindShopsActivity"    android:exported="false" />
        <activity android:name=".ui.customer.ScanActivity"         android:exported="false" />

        <!-- Phase 4 vendor activities -->
        <activity android:name=".ui.vendor.ShowQrActivity"         android:exported="false" />
        <activity android:name=".ui.vendor.RewardRulesActivity"    android:exported="false" />
        <activity android:name=".ui.vendor.AddEditRuleActivity"    android:exported="false" />
        <activity android:name=".ui.vendor.ScanToRedeemActivity"   android:exported="false" />


        <!-- FCM: receives push notifications (Phase 6).
             Declared now so the manifest is complete; the service class is a stub. -->
        <service
            android:name=".service.SapotFcmService"
            android:exported="false">
            <intent-filter>
                <action android:name="com.google.firebase.MESSAGING_EVENT" />
            </intent-filter>
        </service>

    </application>

</manifest>
```

---

## 7. Name Clash Check

### Classes

| Class name | Exists? | Location |
|-----------|---------|----------|
| `MainActivity` | **NOT FOUND** | — |
| `SpinWheelActivity` | **NOT FOUND** | — |
| `MemoryMatchActivity` | **NOT FOUND** | — |
| `WheelView` | **NOT FOUND** | — |
| `GameConfig` | **NOT FOUND** | — |
| `PointsRepository` | **NOT FOUND** (class is named `PointRepository`, no "s") | — |
| `GamesRepository` | **NOT FOUND** | — |

### Layout files

| Layout name | Exists? | Location |
|------------|---------|----------|
| `activity_main` | **NOT FOUND** | — |
| `activity_spin_wheel` | **NOT FOUND** | — |
| `activity_memory_match` | **NOT FOUND** | — |

### View IDs

| ID | Exists? | In which layout |
|----|---------|-----------------|
| `wheel` | **NOT FOUND** | — |
| `resultText` | **NOT FOUND** | — |
| `spinNowButton` | **NOT FOUND** | — |
| `backButton` | **NOT FOUND** | — |
| `infoText` | **NOT FOUND** | — |
| `movesText` | **NOT FOUND** | — |
| `cardGrid` | **NOT FOUND** | — |
| `statusText` | **NOT FOUND** | — |
| `newGameButton` | **NOT FOUND** | — |

### Colors and strings

| Resource | Exists? | Notes |
|----------|---------|-------|
| Color `black` | **NOT FOUND** | No `<color name="black">` in `colors.xml` |
| Color `white` | **NOT FOUND** | No `<color name="white">` in `colors.xml` |
| String `app_name` | **EXISTS** | `strings.xml` line 3: `<string name="app_name">SapotLokal</string>` |

**Summary: No name clashes exist. All the items listed are safe to add.**

---

## 8. Docs Folder

### Everything in `docs/`

| File | Description |
|------|-------------|
| `database-structure.md` | Documents every Firebase node, field, and type |
| `erd-changes.md` | Records every deviation from the original ERD |
| `firebase-rules.json` | The actual Firebase Security Rules (deployed manually) |
| `SapotLokal_ERD.drawio` | ERD source file (Draw.io format) |
| `SapotLokal_ERD.jpg` | ERD image export |
| `SapotLokal_Flow.drawio` | Flowchart source file (Draw.io format) |
| `SAPOTLOKAL_PREREQUISITES.md` | Developer setup instructions |
| `SapotLokal_Simple_App_Guide (2).md` | The Simple App Guide source document |

### Full contents of `docs/erd-changes.md`

```markdown
# SapotLokal — ERD Deviations & Changes

> **Purpose:** Every deliberate deviation from the original ERD is recorded here.
> This ensures the documents and the app never drift apart (build guide Rule 10).
>
> Format: one section per change, with the build guide Open Decision number,
> the reason, and what was changed.

---

## Change 1 — `isActive` field added to `VOUCHER`

**Open Decision:** #11
**Phase added:** Phase 1 (data model)
**Status:** ✅ Approved (build guide explicitly requires this)

**Original ERD:** `VOUCHER` entity has no `isActive` field.

**Problem:** The Simple App Guide says a shop owner can "turn off" a reward rule.
Without an `isActive` field, the only way to turn off a rule is to delete it,
which would orphan any `VOUCHER_USER` records already created from that rule.

**Change:** Add `isActive: Boolean` to the `VOUCHER` entity (defaulting to `true`).

- Setting `isActive = false` hides the rule from new voucher creation.
- Existing `VOUCHER_USER` records remain valid and redeemable.
- Security Rules prevent customers from toggling this field.

---

## Change 2 — `pointsRequiredSnapshot` field added to `VOUCHER_USER`

**Open Decision:** #14
**Phase added:** Phase 1 (data model — proposed; confirmed implementation in Phase 5)
**Status:** ⏳ Proposed — developer must confirm before Phase 5 builds auto-voucher logic

**Original ERD:** `VOUCHER_USER` has no cost field. The `pointsRequired` sits only on
the parent `VOUCHER` (the reward rule).

**Problem:** If a shop owner edits a reward rule's `pointsRequired` after customers
have already earned vouchers from it, the cost of their existing vouchers would
silently change. A customer who earned a voucher when the rule cost 100 points
could find it now costs 200, or vice versa.

**Proposed change:** Copy `pointsRequired` from the rule onto each new `VOUCHER_USER`
as `pointsRequiredSnapshot` at the moment the voucher is created. Redemption uses
`pointsRequiredSnapshot`, not the current rule value.

**Developer action required:** Confirm this change before Phase 5 is built.

---

## Change 3 — QR scanning library changed from ZXing to ML Kit

**Open Decision:** N/A (not in build guide; raised during Phase 1 library selection)
**Phase added:** Phase 1
**Status:** ✅ Approved (developer confirmed in Phase 1 session)

**Original spec:** Build guide Section 2 lists "ZXing" for QR scan + generate.

**Problem:** `zxing-android-embedded` (the Android scanning wrapper) is in maintenance
mode with known camera API incompatibilities on Android 14+.

**Change:**

- **Scanning** → replaced with `com.google.mlkit:barcode-scanning:17.3.0`.
  ML Kit is actively maintained, works on Android 8+, and is free.
- **Generation** → `com.google.zxing:core:3.5.3` is retained. The core library
  (used only to draw QR Bitmaps into an ImageView) is unaffected by the camera
  API issues — it never touches the camera.

The term "ZXing" in the build guide now refers to the core generation library only.
```

### Section 2 version table as it stands now

From `SAPOTLOKAL_BUILD_GUIDE.md`, Section 2:

| Part of the App | Tool | Version | Notes |
|----------------|------|---------|-------|
| Platform | Android | minSdk = Android 8.0 (API 26) or newer | Works on most phones from the last several years |
| App logic | Kotlin | Built-in with AGP 9.4.0 (no separate Kotlin plugin needed) | Kotlin only — no new Java files |
| JDK | Bundled with Android Studio | JDK 17 (minimum required by AGP 9.x) | Used by Gradle to build; no separate JDK install |
| App layout | XML | — | One layout file per screen/component |
| Look and feel | Google Material Design (Material Components for Android) | **1.14.0** | Rounded buttons, simple cards, familiar icons |
| Accounts & login | Firebase Auth | **34.19.0 (via BoM)** | Email + password |
| Live data | Firebase Realtime Database | **34.19.0 (via BoM)** | Stores users, vendors, points, vouchers, games, challenges |
| Notifications | Firebase Cloud Messaging | **34.19.0 (via BoM)** | Level-ups, completed challenges, "reward ready" |
| QR scan | ML Kit Barcode Scanning | **17.3.0** | ⚠️ Replaces ZXing scanning — see `docs/erd-changes.md` Change 3 |
| QR generate | ZXing core | **3.5.3** | Used only to draw QR Bitmaps — no camera |
| Design mockups | Figma | — | Every screen is built to match its Figma design |
| Code storage | GitHub | — | Commit at the end of every phase at minimum |
| IDE | Android Studio (already installed) | Latest stable (AGP 9.4.0, Gradle 9.6.0) | SDK, emulator, and `adb` come with it |

_Note: The build guide lists "JDK 17 (minimum required by AGP 9.x)" but the actual `compileOptions` in `app/build.gradle.kts` uses `JavaVersion.VERSION_21`. The comment in the file says "JDK 21 matches Android Studio's bundled JDK." The version table in the guide has not been updated to reflect JDK 21._

---

## 9. Differences from the Guide

### Things in the code that do not match SAPOTLOKAL_BUILD_GUIDE.md

| # | What the guide says | What the code does | Risk |
|---|--------------------|--------------------|------|
| 1 | Section 2 version table says JDK 17 minimum | `compileOptions` uses `JavaVersion.VERSION_21` | Low — 21 is compatible and is the bundled JDK. Table should be updated. |
| 2 | Rule 11: confirm Gradle JDK is set to Android Studio's bundled JDK; Section 2 says JDK 17 | `app/build.gradle.kts` uses JDK 21 — the table still says JDK 17 | Low — mismatch in documentation only, not a functional problem. |
| 3 | `vendorID` in ERD is described as a Firebase push ID (`-NxAbCd...`) | `SignUpVendorActivity` sets `vendorId = uid` — the vendor ID is the same as the Auth UID, not a separate push ID | Low for now (one vendor per user). Could cause issues if the 1-to-1 assumption is ever changed. |
| 4 | Phase 3 "Debug helper for testing without a second device — clearly marked for removal before Phase 7" | Debug helper exists in both `ScanActivity` (bottom panel) and `ShowQrActivity` (`tvDebugValue` showing raw QR value). Both are marked ⚠️ in comments | Must be removed before Phase 7. |
| 5 | Rule 9: "Every points change is a row in the POINT entity" | `SPIN_WHEEL`, `MEMORY_MATCH`, and `CHALLENGE` sources are defined as constants but never written — games are not built | Expected for current phase; not a bug. |
| 6 | Phase 5 auto-voucher: "whenever a customer's points at a shop change, compare their balance against every active reward rule" | No auto-voucher creation code exists anywhere | Phase 5 is not started. |
| 7 | Security Rules: `challenges` node `".write": "auth != null"` | Any authenticated user (including customers) can write challenge records | A gap: the guide says challenges are "defined once by admin". Any logged-in user can currently create or modify challenges. No admin role or write restriction exists. |
| 8 | `POST_NOTIFICATIONS` runtime permission "done in the Notification setup (Phase 6)" | The permission is declared in the manifest but the runtime request is not implemented | Phase 6 is not started. Reminder for Phase 6. |
| 9 | `CustomerHomeActivity` error from `PointRepository.listenToPointsForUser()` is silently ignored | `onError = { /* keep showing last values; network error handled in ScanActivity */ }` — no user-facing message | The user sees nothing if their points fail to load (e.g., offline). Should show a message. |

### Things that could cause problems when adding two new Activities and a points-writing feature

1. **No Application class / no Firebase persistence:** Firebase is initialised per-call with a URL string each time. When you add game activities, if any of them call `FirebaseDatabase.getInstance()` without the URL by mistake (easy to forget), the app will try to use the wrong regional database and all reads will fail silently or throw a region error. Consider creating an `Application` class that configures Firebase once with the URL.

2. **No `GamesRepository`:** The `GAME` node has Security Rules and a data class, but no repository. If two developers (or two AI sessions) add game code without a central repository, point-writing logic will be duplicated and the ledger pattern (Rule 9) may be broken — e.g., games might write only to `/games` and forget to also write a `POINT` row.

3. **`PointsRepository` name:** The guide and the check list above use `PointsRepository` (with an "s"), but the actual class is `PointRepository` (no "s"). Any new code that imports by the wrong name will fail to compile. Keep the name `PointRepository`.

4. **Activity-per-screen pattern has no shared navigation state:** `SpinWheelActivity` and `MemoryMatchActivity` will need to know the signed-in `userID`. There is no ViewModel or shared state holder. Every new Activity must call `FirebaseAuth.getInstance().currentUser?.uid` itself. This is fine but must be done consistently.

5. **Points `.validate` rule blocks updates:** `"points"` has `".validate": "!data.exists() || newData.val() === data.val()"`. This makes point rows append-only (once written, they cannot change). Game activities that write point rows must use `.push()` to create a new key each time — they must **never** try to update an existing row.

6. **No portrait lock:** If `SpinWheelActivity` has a wheel that should not rotate, it needs `android:screenOrientation="portrait"` or configuration-change handling. Right now no screen locks orientation.

7. **Debug helpers must be removed:** `ScanActivity` has a debug panel (a text field + button to paste a QR code manually). `ShowQrActivity` shows the raw QR value as `tvDebugValue`. Both must be removed or hidden (comment-only visibility is not enough — the code should be deleted per build guide Phase 7 checklist) before shipping.

8. **`CustomerHomeActivity` error swallowing:** If the customer is offline, the points listener fires an error that is silently ignored. The Home screen shows nothing (or stale data). When the Games feature is added (which also needs the user's points balance to check daily limits), this silent failure could cause confusing behaviour — the user might be allowed to spin multiple times a day if the balance check fails silently.

9. **`challenges` security rule is too open:** Any authenticated user can write to `/challenges`. Before the Challenges screen is built in Phase 6, the write rule should be tightened so only an admin or a vendor can create challenges.

---

## Items Marked NOT FOUND

| Section | Item |
|---------|------|
| 1 | My Vouchers screen (Activity and layout) |
| 1 | Level-up moment / animation code |
| 5 | `values-night` folder (no dark mode) |
| 5 | `dimens.xml` |
| 5 | `styles.xml` (separate from themes.xml) |
| 5 | Portrait lock (`android:screenOrientation`) |
| 6 | `MAIN_APP_REPORT.md` (this file did not exist before; created now) |
| 7 | Class: `MainActivity` |
| 7 | Class: `SpinWheelActivity` |
| 7 | Class: `MemoryMatchActivity` |
| 7 | Class: `WheelView` |
| 7 | Class: `GameConfig` |
| 7 | Class: `PointsRepository` (exists as `PointRepository` without "s") |
| 7 | Class: `GamesRepository` |
| 7 | Layout: `activity_main` |
| 7 | Layout: `activity_spin_wheel` |
| 7 | Layout: `activity_memory_match` |
| 7 | ID: `wheel` |
| 7 | ID: `resultText` |
| 7 | ID: `spinNowButton` |
| 7 | ID: `backButton` |
| 7 | ID: `infoText` |
| 7 | ID: `movesText` |
| 7 | ID: `cardGrid` |
| 7 | ID: `statusText` |
| 7 | ID: `newGameButton` |
| 7 | Color: `black` |
| 7 | Color: `white` |
| 4 | Any class writing to or reading from `/games` node |
| 4 | `GamesRepository` |
| 4 | `setPersistenceEnabled(true)` call |
| 4 | Custom `Application` class |
| 3 | Firebase config node for tunable numbers (config lives in `AppConfig.kt`, a Kotlin object, not a Firebase node) |
| 9 | Admin role or admin-only write rules for challenges |

