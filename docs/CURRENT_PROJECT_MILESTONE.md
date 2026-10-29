# SapotLokal — Project Milestone & Branch Architecture Report

_Document Version:_ 1.0  
_Date:_ October 29, 2026  
_Repository:_ `c:\dev\sapotlokal`  
_Current Active Branch:_ `feature/games-merge`  
_Build Status:_ `assembleDebug` Passing (Gradle 9.6.0, AGP 9.4.0, JDK 21, compileSdk 37, minSdk 26)

---

## 1. Executive Summary & Current Milestone

The **SapotLokal** mobile application is an Android-native hyperlocal merchant loyalty and rewards ecosystem designed to encourage Malaysian consumers to support local businesses ("Sapot Lokal"). Through gamification, QR code transactions, and dynamic loyalty mechanics, shoppers earn points that convert into merchant-specific vouchers.

### 1.1 Project Milestone Overview

The project is currently at **Milestone 6 (Advanced Gamification, Loyalty Expansion & UI Overhaul)**, transitioning into **Milestone 7 (Polish, Security Hardening & Release Verification)**.

| Phase | Milestone Name | Status | Completion | Key Delivered Capabilities |
| :--- | :--- | :---: | :---: | :--- |
| **Phase 1** | Foundations & Data Model | **DONE** | 100% | Regional Firebase RTDB (`asia-southeast1`), Firebase Auth, ERD data models, centralized `AppConfig.kt`, base themes, `SplashActivity` router. |
| **Phase 2** | Accounts & Role-Based Auth | **DONE** | 100% | Role picker, Customer sign-up, Vendor sign-up with strict SSM validation, Customer/Vendor login, database security rules with immutable role locking. |
| **Phase 3** | Customer Core Loop | **DONE** | 100% | CameraX + Google ML Kit barcode scanning (`ScanActivity`), rotating QR validation, append-only point ledger (`PointRepository`), `FindShopsActivity`. |
| **Phase 4** | Shop Owner Core Loop | **DONE** | 100% | 60-second TOTP rotating QR generator (`ShowQrActivity`), reward rules manager (`RewardRulesActivity`, `AddEditRuleActivity`), voucher redemption desk (`ScanToRedeemActivity`). |
| **Phase 5** | Rewards & Vouchers | **DONE** | 95% | Customer voucher wallet (`MyVouchersActivity`), voucher QR generation (`dialog_voucher_qr.xml`), auto-voucher issuance rules in `VoucherRepository`, atomic redemption ledger updates. |
| **Phase 6** | Games, Levels & Challenges | **DONE** | 90% | Mini-games engine merged (`GamesMenuActivity`, `SpinWheelActivity`, `MemoryMatchActivity`), custom `WheelView`, daily caps enforcement via server-side `/games` queries, Customer Challenges (`ChallengesActivity`, `ChallengeRepository`), Vendor Portal (`VendorStatsActivity`, `ShopSettingsActivity`, `VendorLoginActivity`), push notification handler (`SapotFcmService`). |
| **Phase 7** | Polish & Final Testing | **IN PROGRESS** | 40% | Full design tokens implemented (Inter font, brand colors, badge/pill drawables), layout overhauls completed. Remaining: removing debug panels from `ScanActivity`/`ShowQrActivity`, camera permission fallback UI, offline banner. |

---

## 2. Branch Comparison: `main` vs. `feature/games-merge`

The repository history reflects a staged evolutionary development:
* **`main`** contains the initial foundation, authentication, and core Phase 3–4 loops.
* **`feature/games-merge`** contains the full standalone games integration (Phases 1–6 of `GAMES_MERGE_BUILD_GUIDE.md`) plus the comprehensive UI layout overhaul, database index optimizations, customer vouchers screen, challenges engine, and vendor management portal (Commit `29ae011`).

### 2.1 Branch Summary Table

| Architectural Layer / Feature | `main` Branch | `feature/games-merge` Branch |
| :--- | :--- | :--- |
| **Git Commit Reference** | `ee6b354` ("Add Android project setup...") | `29ae011` ("feat(ui, firebase): overhaul XML layouts...") |
| **Mini-Games Engine** | ❌ None (placeholder button showing toast) | ✅ **Full Engine**: `GamesMenuActivity`, `SpinWheelActivity` (`WheelView`), `MemoryMatchActivity`, `GameConfig.kt`, `GamesRepository.kt` |
| **Customer Vouchers** | ⚠️ Partial: Backend redemption only; no UI | ✅ **Full Wallet**: `MyVouchersActivity`, `VoucherAdapter`, `dialog_voucher_qr.xml`, auto-voucher generation hooks |
| **Customer Challenges** | ❌ None | ✅ **Full Engine**: `ChallengesActivity`, `ChallengeAdapter`, `ChallengeRepository.kt`, `item_challenge_card.xml` |
| **Vendor Management** | ⚠️ Basic: Only QR display, rules & scan redeem | ✅ **Extended Portal**: `VendorStatsActivity`, `ShopSettingsActivity`, `VendorLoginActivity`, `VendorTransactionAdapter`, `VendorRepository.kt` |
| **Customer Home Screen** | ⚠️ Basic: Simple card layout with text buttons | ✅ **Overhauled Dashboard**: Tier progress bar, reward nudges, recent shops carousel (`RecentShopAdapter`), modern bottom navigation bar |
| **Authentication Flow** | Shared generic `LoginActivity` | Dedicated `VendorLoginActivity` + role-specific customer welcome and login screens |
| **Push Notifications** | ⚠️ `SapotFcmService.kt` was an empty stub | ✅ Fully implemented FCM listener with token persistence and notification channels |
| **Database Rules & Indexing** | Root rules without query index optimizations | Optimized with `.indexOn` keys on `/points`, `/vouchers`, `/voucherUsers`, `/games`, `/challenges`, and `/userChallenges` |
| **UI Design System** | Default MaterialComponents styles | Brand design system: Inter font typography, custom pill/badge drawables, custom state-list buttons, unified color palette |

### 2.2 Detailed File and Component Delta

#### A. Activities and UI Controllers
* **Only in `feature/games-merge`:**
  * `ui/games/GamesMenuActivity.kt` — Mini-game hub with daily quota indicators.
  * `ui/games/SpinWheelActivity.kt` — Daily lucky wheel with canvas physics.
  * `ui/games/WheelView.kt` — Custom canvas drawing component for the 8-segment wheel.
  * `ui/games/MemoryMatchActivity.kt` — 4x3 interactive emoji memory matching game.
  * `ui/customer/MyVouchersActivity.kt` — Customer voucher wallet with tab filters.
  * `ui/customer/ChallengesActivity.kt` — Quests and daily loyalty missions screen.
  * `ui/auth/VendorLoginActivity.kt` — Specialized shop owner sign-in portal.
  * `ui/vendor/VendorStatsActivity.kt` — Merchant analytics & transaction logs.
  * `ui/vendor/ShopSettingsActivity.kt` — Store profile and metadata management.

#### B. Repositories and Data Services
* **Only in `feature/games-merge`:**
  * `data/repository/GamesRepository.kt` — Manages game play limits and atomic Firebase writes to `/games` and `/points`.
  * `data/repository/ChallengeRepository.kt` — Loads challenges and user progress, handles claim reward transactions.
  * `data/repository/VendorRepository.kt` — Loads vendor profile details, transaction history, and store statistics.
  * `ui/games/GameConfig.kt` — UI and animation configuration for games.
  * `util/NetworkUtils.kt` — Network reachability detection.
* **Expanded in `feature/games-merge`:**
  * `data/repository/VoucherRepository.kt` — Added customer-side queries (`getUserVouchers()`), active rule checking, and voucher code generator.
  * `data/repository/PointRepository.kt` — Added helper queries for transaction history and recent visits.
  * `service/SapotFcmService.kt` — Implemented token sync and Android notification channels.
  * `AppConfig.kt` — Extended with game reward limits (`SPIN_WHEEL_MIN/MAX_POINTS`, `MEMORY_MATCH_DAILY_CAP_POINTS`, `GAMES_RESET_TIME_ZONE`).

#### C. Layouts and Resources
* **Layouts added in `feature/games-merge`:**
  * `activity_games_menu.xml`, `activity_spin_wheel.xml`, `activity_memory_match.xml`
  * `activity_my_vouchers.xml`, `dialog_voucher_qr.xml`, `item_voucher_card.xml`
  * `activity_challenges.xml`, `item_challenge_card.xml`
  * `activity_vendor_login.xml`, `activity_vendor_stats.xml`, `activity_shop_settings.xml`, `item_vendor_transaction.xml`
  * `activity_customer_welcome.xml`, `item_shop_card.xml`, `layout_customer_bottom_nav.xml`
* **Layouts redesigned in `feature/games-merge`:**
  * `activity_customer_home.xml` — Completely revamped with curved elevation cards, tier meter, horizontal recent shops RecyclerView, and bottom navigation.
  * `activity_login.xml` — Modernized with brand typography and input outlines.

---

## 3. End-to-End Application Flow: Customer Side

The customer journey is designed around discovery, instant reward feedback, and gamified engagement.

```
[SplashActivity]
       │
       ▼
[RolePickerActivity] ──(Select Customer)──► [SignUpCustomerActivity]
       │                                              │
       ▼ (Already registered)                         ▼
[LoginActivity] ───────────────────────────► [CustomerHomeActivity]
                                                      │
         ┌───────────────────┬────────────────────────┼────────────────────────┬───────────────────┐
         ▼                   ▼                        ▼                        ▼                   ▼
 [FindShopsActivity]   [ScanActivity]       [MyVouchersActivity]     [GamesMenuActivity]   [ChallengesActivity]
                       (CameraX + ML Kit)   (Voucher QR Dialog)               │             (Quests & Claims)
                                                               ┌──────────────┴──────────────┐
                                                               ▼                             ▼
                                                      [SpinWheelActivity]           [MemoryMatchActivity]
```

### Step 1: App Launch & Authentication Routing
1. **`SplashActivity`:** Checks `FirebaseAuth.currentUser`.
   * If not logged in: Navigates to `RolePickerActivity`.
   * If logged in: Queries `/users/{uid}/role` from the regional Realtime Database (`asia-southeast1`). If `role == "customer"`, routes to `CustomerHomeActivity`.
2. **`RolePickerActivity`:** Welcomes first-time users and presents two distinct cards: "Customer" and "Shop Owner". Selecting "Customer" launches `SignUpCustomerActivity` (with a shortcut to `LoginActivity`).
3. **`SignUpCustomerActivity`:**
   * Collects: Full Name, Email Address, Password, and Password Confirmation.
   * Creates Firebase Auth account via `createUserWithEmailAndPassword()`.
   * Atomically writes to `/users/{uid}`: `User(userID, userName, userEmail, role = "customer")`.
   * Automatically transitions to `CustomerHomeActivity`.
4. **`LoginActivity`:** Allows returning customers to log in using their email and password. Verifies that the authenticated user's role is `"customer"` before entering the customer dashboard.

### Step 2: Customer Home Dashboard (`CustomerHomeActivity`)
The customer home screen serves as the operational center:
* **Live Points & Level Meter:** Attaches a live `ValueEventListener` to `/points` filtered by `userID`.
  * Computes lifetime points and current spendable points.
  * Determines user tier via `AppConfig.levelForPoints(points)`: **Bronze** (0–99 pts), **Silver** (100–249 pts), **Gold** (250–499 pts), or **Platinum** (500+ pts).
  * Updates a visual progress bar indicating points needed to reach the next tier.
* **Reward Nudges:** Dynamic banner alerting users when a voucher is ready for redemption or when daily games are available.
* **Recent Shops Visited:** Horizontal `RecyclerView` (`RecentShopAdapter`) showing local shops where points were recently earned.
* **Quick Navigation & Bottom Bar:**
  * **Scan:** Floating Action Button (FAB) or central action triggering the QR scanner.
  * **Explore:** Navigates to `FindShopsActivity`.
  * **Games:** Launches `GamesMenuActivity`.
  * **Vouchers:** Launches `MyVouchersActivity`.
  * **Challenges:** Launches `ChallengesActivity`.

### Step 3: Discovering Local Shops (`FindShopsActivity`)
* Queries all registered merchants at `/vendors`.
* Cross-references with the customer's point ledger at `/points` to display how many points the customer has earned at each specific shop.
* Displays shop name, business category (e.g., Cafe, Bakery, Retail), and physical address.

### Step 4: Scanning In-Store QR to Earn Points (`ScanActivity`)
* **CameraX & Google ML Kit:** Live camera feed with barcode analysis scanning QR codes displayed by vendors.
* **Payload Verification:** Parses standard payload `sapotlokal:shop:{vendorId}:{token}`.
* **Validation Pipeline:**
  1. Checks token existence under `/qrcodes/{vendorId}` in Firebase.
  2. Verifies code has not expired (`qrcodeExpiryDate > now`).
  3. Verifies daily visit cap (customer can only earn visit points at the same shop once per calendar day).
* **Ledger Write:** Calls `PointRepository.recordShopVisit()`, appending a record to `/points` with `pointEarnedAmount = 10` and `pointSource = PointSource.SHOP_VISIT`.
* **Auto-Voucher Trigger:** Scans active rules at `/vouchers` for this vendor; if points cross the requirement, auto-generates a `VoucherUser` record.
* **Success Feedback:** Displays confirmation dialog showing points earned and updated shop balance.

### Step 5: Managing & Displaying Vouchers (`MyVouchersActivity`)
* Queries `/voucherUsers` where `userID == currentUserId`.
* Displays list using `VoucherAdapter` (`item_voucher_card.xml`):
  * Filter tabs: **Active**, **Used**, **Expired**.
  * Shows voucher title, required points, expiry date, and a **"Best Choice"** badge on the highest-value qualifying voucher.
* **Redemption QR Display (`dialog_voucher_qr.xml`):**
  * Tapping a voucher opens a full-screen dialog containing:
    1. A ZXing-generated 2D QR code encoding `sapotlokal:voucher:{voucherUserId}:{voucherCode}`.
    2. A clean, human-readable 8-character alphanumeric voucher code for manual entry by the vendor if the camera is unavailable.

### Step 6: Gamification & Mini-Games (`GamesMenuActivity`)
* Displays daily mini-game availability in `Asia/Kuala_Lumpur` timezone:
  * **Daily Spin Wheel:** 1 free spin per calendar day.
  * **Memory Match:** Shows points won today against the daily cap (e.g., 20/60 points).
* **Spin The Wheel (`SpinWheelActivity`):**
  * Custom `WheelView` renders an 8-segment wheel with alternating brand colors.
  * On spin tap, generates reward (5 to 50 points), animates wheel with deceleration, and lands on winning segment.
  * Checks live `/games` node for today's date; on win, atomically writes `/games` row and `/points` row (`vendorID = ""`, `pointSource = PointSource.SPIN_WHEEL`).
* **Memory Match (`MemoryMatchActivity`):**
  * 4x3 card grid with 6 emoji pairs.
  * Card flip animation. If 2 flipped cards match, they stay revealed; otherwise, they flip back after 800ms.
  * Matching all 6 pairs wins 20 points, up to a maximum daily cap of 60 points (3 wins).
  * Writes win record to `/games` and points to `/points` (`pointSource = PointSource.MEMORY_MATCH`).

### Step 7: Loyalty Challenges (`ChallengesActivity`)
* Displays active quest cards using `ChallengeAdapter` (`item_challenge_card.xml`).
* Tracks missions such as "Visit 3 different cafes" or "Earn 100 points".
* Dynamic progress bar showing current count versus target count.
* When progress reaches 100%, reveals a **"Claim Reward"** button that issues bonus points via `ChallengeRepository.claimChallengeReward()`.

---

## 4. End-to-End Application Flow: Vendor Side (Shop Owner)

The shop owner experience is designed for speed at the checkout counter and clear business insights.

```
[SplashActivity]
       │
       ▼
[RolePickerActivity] ──(Select Shop Owner)──► [SignUpVendorActivity]
       │                                                │
       ▼ (Already registered)                           ▼
[VendorLoginActivity] ────────────────────────► [VendorHomeActivity]
                                                        │
         ┌───────────────────┬──────────────────────────┼──────────────────────────┐
         ▼                   ▼                          ▼                          ▼
 [ShowQrActivity]    [RewardRulesActivity]    [ScanToRedeemActivity]     [VendorStatsActivity]
 (60s Rotating QR)   (Manage Discounts)       (CameraX + Manual Code)    (Store Analytics)
                             │
                             ▼
                   [AddEditRuleActivity]
```

### Step 1: Merchant Registration & Authentication
1. **`RolePickerActivity`:** Vendor selects "Shop Owner".
2. **`SignUpVendorActivity`:**
   * Collects Business Information:
     * Shop Name (e.g., "Kopi Kenangan Bangsar")
     * Business Category (Food & Beverage, Grocery, Services, Retail, etc.)
     * Business Address
     * SSM Registration Number (Suruhanjaya Syarikat Malaysia format validated)
     * Owner Name, Email Address, and Password
   * Firebase Auth creates the account.
   * Atomic sequential database write:
     * `/users/{uid}`: `User(userID, userName, userEmail, role = "vendor")`
     * `/vendors/{uid}`: `Vendor(vendorID, userID, vendorName, vendorCategory, vendorAddress, ssmNumber)`
   * Navigates directly to `VendorHomeActivity`.
3. **`VendorLoginActivity`:** Dedicated merchant login screen. Rejects any user credentials associated with `role == "customer"` to prevent role confusion.

### Step 2: Vendor Home Dashboard (`VendorHomeActivity`)
A high-contrast, clean action-oriented dashboard providing instant access to merchant tools:
* **Header:** Displays shop name, category badge, and active status.
* **Quick Stats Preview:** Today's customer scan count and redemptions.
* **Primary Operation Cards:**
  1. **"Display Shop QR"** — Countertop QR display for customer points earning.
  2. **"Redeem Customer Voucher"** — Scanner to validate customer reward vouchers.
  3. **"Reward Rules"** — Configure points-to-reward thresholds.
  4. **"Store Analytics"** — Deep dive into customer visit and redemption stats.
  5. **"Shop Settings"** — Update store contact details and operating information.

### Step 3: Countertop Rotating QR Code (`ShowQrActivity`)
* **Dynamic Security Protocol:**
  * Uses ZXing to generate a high-contrast 2D QR code.
  * Operates on a 60-second `CountDownTimer` (`AppConfig.QR_ROTATION_SECONDS`).
  * Every 60 seconds, generates a cryptographically secure random token.
  * Writes token to `/qrcodes/{vendorId}` with `qrcodeGenerateDate` and `qrcodeExpiryDate`.
  * Visual progress ring counts down from 60 to 0 seconds, regenerating the QR image automatically.
  * Prevents fraudulent static screenshots from being used to harvest points outside the store.

### Step 4: Configuring Reward Rules (`RewardRulesActivity` & `AddEditRuleActivity`)
* **`RewardRulesActivity`:** Lists all reward thresholds set by this merchant stored at `/vouchers`.
  * Each card shows: Reward Title (e.g., "RM5 Cash Voucher"), Points Required (e.g., "50 pts"), Description, and an Active/Inactive switch.
* **`AddEditRuleActivity`:**
  * Form allowing the merchant to create a new reward or edit an existing one.
  * Fields: Reward Title, Description, Required Points, and Active status.
  * Deactivating a rule stops new customer vouchers from generating without invalidating existing issued vouchers.

### Step 5: Voucher Redemption Desk (`ScanToRedeemActivity`)
At the checkout counter, the customer presents their voucher QR or 8-character code:
1. **CameraX Barcode Scanning:** Vendor points camera at customer's phone to scan `sapotlokal:voucher:{voucherUserId}:{voucherCode}`.
2. **Manual Input Fallback:** If camera scanning fails or screen brightness is low, vendor can type the 8-character alphanumeric code.
3. **Validation Pipeline (`VoucherRepository.validateVoucherForRedemption()`):**
   * Verifies voucher exists in `/voucherUsers`.
   * Verifies voucher belongs to this specific vendor (`voucher.vendorID == currentVendorId`).
   * Verifies voucher status is `"active"`.
   * Verifies voucher has not expired (`voucherExpiryDate > now`).
   * Verifies customer's current point balance at this shop covers `pointsRequiredSnapshot`.
4. **Atomic Ledger Deduction (`VoucherRepository.redeemVoucher()`):**
   * Performs an atomic multi-path `updateChildren()`:
     * `/voucherUsers/{voucherUserId}/voucherStatus` → `"used"`
     * `/voucherUsers/{voucherUserId}/redeemedAt` → `System.currentTimeMillis()`
     * Appends a new `/points` ledger row with `pointRedeemedAmount = pointsRequiredSnapshot`, `pointEarnedAmount = 0`, and `pointSource = PointSource.REDEMPTION`.
5. **Success Screen:** Displays a prominent green confirmation with the customer's name, discount description, and remaining point balance.

### Step 6: Store Analytics & History (`VendorStatsActivity`)
* **Merchant Metrics Dashboard:**
  * Total Unique Customers served.
  * Total Loyalty Points distributed to date.
  * Total Vouchers successfully redeemed.
  * Estimated customer retention rate.
* **Transaction Ledger (`VendorTransactionAdapter` & `item_vendor_transaction.xml`):**
  * Real-time stream of scans and redemptions.
  * Shows timestamp, customer ID snapshot, transaction type (+10 Visit, -50 Voucher), and status.

### Step 7: Store Settings & Profile (`ShopSettingsActivity`)
* Allows merchants to update:
  * Store Display Name
  * Category / Cuisine
  * Operating Hours
  * Physical Store Address
  * Public Merchant Description / Story
* Saves updates directly to `/vendors/{vendorId}` in Firebase Realtime Database.

---

## 5. Firebase Realtime Database Schema

All database references use the regional URL `https://sapotlokal-cd37b-default-rtdb.asia-southeast1.firebasedatabase.app`.

```
/users/{userID}
    ├── userID: String
    ├── userName: String
    ├── userEmail: String
    ├── role: "customer" | "vendor"  (write-once, immutable)
    └── fcmToken: String?

/vendors/{vendorID}
    ├── vendorID: String
    ├── userID: String (FK -> users)
    ├── vendorName: String
    ├── vendorCategory: String
    ├── vendorAddress: String
    ├── ssmNumber: String
    └── vendorDescription: String

/qrcodes/{vendorID}
    ├── qrcodeID: String
    ├── vendorID: String
    ├── qrcodeValue: String
    ├── qrcodeGenerateDate: Long
    ├── qrcodeExpiryDate: Long
    └── isActive: Boolean

/points/{pointID}  (Append-only ledger)
    ├── pointID: String
    ├── userID: String
    ├── vendorID: String (Empty "" for mini-game bonus points)
    ├── pointEarnedAmount: Int
    ├── pointRedeemedAmount: Int
    └── pointSource: "shop_visit" | "spin_wheel" | "memory_match" | "challenge" | "redemption"

/vouchers/{voucherID}  (Merchant reward rules)
    ├── voucherID: String
    ├── vendorID: String
    ├── voucherName: String
    ├── voucherDescription: String
    ├── pointsRequired: Int
    └── isActive: Boolean

/voucherUsers/{voucherUserID}  (Issued customer vouchers)
    ├── voucherUserID: String
    ├── userID: String
    ├── voucherID: String
    ├── vendorID: String
    ├── voucherCode: String (8-char alphanumeric)
    ├── voucherStatus: "active" | "used" | "expired"
    ├── voucherExpiryDate: Long
    ├── pointsRequiredSnapshot: Int
    ├── voucherTitle: String
    └── createdAt: Long

/games/{gameID}  (Audit trail of all mini-game plays)
    ├── gameID: String
    ├── userID: String
    ├── gameType: "spin_wheel" | "memory_match"
    ├── gameAttempt: Int
    ├── gamePointsRewarded: Int
    ├── gameDay: String ("yyyy-MM-dd" in Asia/Kuala_Lumpur)
    └── gamePlayedAt: Long

/challenges/{challengeID}  (Admin-configured missions)
    ├── challengeID: String
    ├── challengeTitle: String
    ├── challengeDescription: String
    ├── challengeTargetValue: Int
    ├── challengeRewardPoint: Int
    └── isActive: Boolean

/userChallenges/{userChallengeID}
    ├── userChallengeID: String
    ├── userID: String
    ├── challengeID: String
    ├── currentValue: Int
    └── userChallengeStatus: "in_progress" | "completed" | "claimed"
```

---

## 6. Current Implementation Gaps & Next Steps (Phase 7 Polish)

With all functional screens implemented and passing build compilation in `feature/games-merge`, the remaining checklist before production release involves:

1. **Remove Development Debug Panels:**
   * Remove the manual string injector panel in `ScanActivity.kt`.
   * Remove raw token preview debug text in `ShowQrActivity.kt`.
2. **Camera Permission Denied Edge Case:**
   * Provide an explicit permission primer dialog and direct navigation to App Settings if camera access is permanently denied.
3. **Global Offline Banner:**
   * Integrate `NetworkUtils.kt` with a persistent top banner indicating when device has lost internet connectivity.
4. **Automated Testing:**
   * Write unit tests for `AppConfig.levelForPoints()`, `VoucherRepository` redemption math, and `GamesRepository` daily cap calculations.
5. **Merge to Main:**
   * Perform final code review and merge `feature/games-merge` into `main`.

