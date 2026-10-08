# SapotLokal — Firebase Realtime Database Structure

> **Source of truth:** This file documents the JSON tree layout for every entity defined in the ERD (build guide Section 3.4). It is updated whenever a field or node is added. Any deviation from the original ERD is listed in `docs/erd-changes.md`.

---

## Top-Level Nodes

```
sapotlokal-db (root)
├── users/
├── vendors/
├── qrcodes/
├── points/
├── vouchers/
├── voucherUsers/
├── games/
├── challenges/
└── userChallenges/
```

Each node is **keyed by the entity's ID** (a Firebase push ID, e.g. `-NxAbCd...`).

---

## Node Details

### `/users/{userID}`

| Field       | Type   | Notes                                                                                            |
| ----------- | ------ | ------------------------------------------------------------------------------------------------ |
| `userID`    | String | Same as the Firebase Auth UID                                                                    |
| `userName`  | String | Display name                                                                                     |
| `userEmail` | String | Same as Auth email                                                                               |
| `role`      | String | `"customer"` or `"vendor"` — written once at sign-up; Security Rules block writes after creation |

### `/vendors/{vendorID}`

| Field            | Type   | Notes                                                  |
| ---------------- | ------ | ------------------------------------------------------ |
| `vendorID`       | String | Firebase push ID                                       |
| `userID`         | String | FK → `/users/{userID}`                                 |
| `vendorName`     | String | Shop name                                              |
| `vendorCategory` | String | e.g. `"Food & Beverage"`                               |
| `vendorAddress`  | String | Free-text address                                      |
| `ssmNumber`      | String | Required at sign-up; stored as-is, never auto-verified |

### `/qrcodes/{qrcodeID}`

| Field                | Type    | Notes                                              |
| -------------------- | ------- | -------------------------------------------------- |
| `qrcodeID`           | String  | Firebase push ID                                   |
| `vendorID`           | String  | FK → `/vendors/{vendorID}`                         |
| `qrcodeValue`        | String  | Secret value encoded in the QR; rotates every 60 s |
| `qrcodeGenerateDate` | Long    | Unix epoch ms                                      |
| `qrcodeExpiryDate`   | Long    | Unix epoch ms (`qrcodeGenerateDate + 60 000`)      |
| `isActive`           | Boolean | Only one active code per vendor at a time          |

### `/points/{pointID}`

Ledger — every change is a **new row**. Balances are always computed by summing rows, never stored as a single overwritten total.

| Field                 | Type   | Notes                                                                               |
| --------------------- | ------ | ----------------------------------------------------------------------------------- |
| `pointID`             | String | Firebase push ID                                                                    |
| `userID`              | String | FK → `/users/{userID}`                                                              |
| `vendorID`            | String | FK → `/vendors/{vendorID}`                                                          |
| `pointEarnedAmount`   | Int    | > 0 for earn events; 0 for redemption rows                                          |
| `pointRedeemedAmount` | Int    | > 0 for redemption rows; 0 for earn events                                          |
| `pointSource`         | String | `"shop_visit"` / `"spin_wheel"` / `"memory_match"` / `"challenge"` / `"redemption"` |

**Balance formula (per customer per shop):**

```
balance = SUM(pointEarnedAmount) − SUM(pointRedeemedAmount)
          WHERE userID = X AND vendorID = Y
```

**Lifetime earned (for level calculation):**

```
lifetime = SUM(pointEarnedAmount) WHERE userID = X
```

### `/vouchers/{voucherID}` — Reward Rule

Created by the shop owner. **Not** a per-customer voucher — this is the template.

| Field                | Type    | Notes                                                              |
| -------------------- | ------- | ------------------------------------------------------------------ |
| `voucherID`          | String  | Firebase push ID                                                   |
| `vendorID`           | String  | FK → `/vendors/{vendorID}`                                         |
| `voucherName`        | String  | e.g. `"Free Cake"`                                                 |
| `voucherDescription` | String  | e.g. `"One free slice of the daily cake"`                          |
| `pointsRequired`     | Int     | Threshold to unlock this reward                                    |
| `isActive`           | Boolean | **ERD addition** — see `erd-changes.md`. `false` = rule turned off |

### `/voucherUsers/{voucherUserID}` — Customer Voucher

Auto-created when a customer's per-shop balance crosses a reward rule's `pointsRequired`.

| Field                    | Type   | Notes                                                                 |
| ------------------------ | ------ | --------------------------------------------------------------------- |
| `voucherUserID`          | String | Firebase push ID                                                      |
| `userID`                 | String | FK → `/users/{userID}`                                                |
| `voucherID`              | String | FK → `/vouchers/{voucherID}` (the rule)                               |
| `vendorID`               | String | Denormalised FK for per-shop queries                                  |
| `voucherCode`            | String | Unique, human-typeable code (e.g. `"SL-7X4K2"`)                       |
| `voucherStatus`          | String | `"active"` / `"used"` / `"expired"`                                   |
| `voucherExpiryDate`      | Long   | Unix epoch ms (`creationTime + AppConfig.VOUCHER_EXPIRY_DAYS`)        |
| `pointsRequiredSnapshot` | Int    | **ERD addition** — see `erd-changes.md`. Cost snapshotted at creation |

### `/games/{gameID}`

| Field                | Type   | Notes                                       |
| -------------------- | ------ | ------------------------------------------- |
| `gameID`             | String | Firebase push ID                            |
| `userID`             | String | FK → `/users/{userID}`                      |
| `gameType`           | String | `"spin_wheel"` or `"memory_match"`          |
| `gameAttempt`        | Int    | Cumulative attempt count for this game type |
| `gamePointsRewarded` | Int    | Points awarded in this game play            |

### `/challenges/{challengeID}`

| Field                  | Type    | Notes                            |
| ---------------------- | ------- | -------------------------------- |
| `challengeID`          | String  | Firebase push ID                 |
| `challengeTitle`       | String  | e.g. `"Visit 5 shops this week"` |
| `challengeDescription` | String  | Longer explanation               |
| `challengeTargetValue` | Int     | e.g. `5`                         |
| `challengeRewardPoint` | Int     | Points awarded on completion     |
| `isActive`             | Boolean | `false` = hidden from users      |

### `/userChallenges/{userChallengeID}`

| Field                 | Type   | Notes                            |
| --------------------- | ------ | -------------------------------- |
| `userChallengeID`     | String | Firebase push ID                 |
| `userID`              | String | FK → `/users/{userID}`           |
| `challengeID`         | String | FK → `/challenges/{challengeID}` |
| `currentValue`        | Int    | User's current progress          |
| `userChallengeStatus` | String | `"in_progress"` or `"completed"` |

---

## Indexing Recommendations

Add these `.indexOn` rules to the Security Rules (already included in `docs/firebase-rules.json`) to avoid full-tree scans:

| Node              | Index field                           | Why                                   |
| ----------------- | ------------------------------------- | ------------------------------------- |
| `/points`         | `userID`, `vendorID`                  | Balance queries filter by both        |
| `/voucherUsers`   | `userID`, `vendorID`, `voucherStatus` | My Vouchers and redemption checks     |
| `/qrcodes`        | `vendorID`, `isActive`                | Finding the current code for a vendor |
| `/vouchers`       | `vendorID`, `isActive`                | Listing a shop's active reward rules  |
| `/userChallenges` | `userID`                              | Challenges list for a customer        |
| `/games`          | `userID`, `gameType`                  | Daily spin-wheel limit check          |
