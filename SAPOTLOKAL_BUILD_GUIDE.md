# SapotLokal — AI-Assisted Build Guide

**Purpose of this file:** This is a working spec for an AI coding assistant (Claude Code, Copilot, etc.) to guide the developer through building SapotLokal phase by phase. Drop this file in the repo root. When starting a session, tell the AI: _"Read SAPOTLOKAL_BUILD_GUIDE.md and start Phase 1."_

---

## 0. Project Snapshot

- **Project:** SapotLokal — mobile app that rewards customers with points and vouchers for supporting local shops
- **Users:** Two roles only — **Customer** and **Shop Owner (Vendor)**. Role is chosen once at sign-up and stored on the account; login never asks again
- **Core loop:** Customer scans the shop's rotating QR → earns points → points cross a shop's reward threshold → app auto-creates a voucher → customer shows voucher QR → shop owner scans/enters it → voucher marked used **and its point cost is deducted**
- **Target platform:** Android only, **Android 8.0 or newer** (do not suggest iOS builds, Flutter, React Native, or Mac-only tooling)
- **Developer environment:** Latest stable **Android Studio** is already installed and uses its **bundled JDK** (no separate JDK is installed). The developer's OS is not yet confirmed — the AI must ask once at the start of Phase 1 and label every command block with the shell it is meant for
- **Source documents:** This guide is derived only from three project documents — the **Simple App Guide**, the **Flowchart** (Customer + Vendor), and the **ERD**. Do not invent features, screens, or tools that appear in none of them. If something is missing, ask
- **Status of the ERD and Flowchart:** They are **decisive but not final**. Follow them by default, but the AI may propose changes to either one (see Rule 10). The one confirmed override so far: **vouchers cost points when used** (see Section 3.3)
- **Suggested repo structure:**
  ```
  sapotlokal/
  ├── app/            # Android Studio module (Kotlin + XML)
  ├── docs/           # Flowchart, ERD, Figma exports, notes, screenshots
  └── SAPOTLOKAL_BUILD_GUIDE.md
  ```

---

## 1. Rules for the AI Assistant (read this before writing any code)

1. **Work one phase at a time.** Do not jump ahead to a later phase's code even if it seems convenient. Finish and confirm the "Definition of Done" checklist for the current phase before moving on.
2. **Explain before/after generating code.** For every non-trivial file or block, give a short plain-language explanation (2–5 sentences) of what it does and why it's structured that way — this developer needs to understand it well enough to explain and defend it, not just have working code.
3. **State the environment for every command.** Say clearly which shell or tool each command block is meant for (Android Studio terminal, PowerShell, Firebase console, etc.). Ask the developer's OS first; don't assume.
4. **Android only, Kotlin + XML.** Write all app logic in **Kotlin** (not Java). Screens are built with XML layouts plus Kotlin activities/fragments, styled with Google Material Design to match the Figma designs. Do not use Jetpack Compose or cross-platform frameworks unless the developer explicitly asks — if Compose seems better for something, propose it and ask first.
5. **Never commit secrets.** Anything sensitive or project-specific (e.g., `google-services.json`, any signing keys, any TOTP secrets) goes in files that are `.gitignore`d. When such a file is needed, also generate a matching placeholder/example file with dummy values and document how to obtain the real one.
6. **Follow the confirmed scope decisions below** — do not silently re-introduce complexity that was deliberately cut, and do not silently drop behavior that was deliberately included:
   - **SSM Registration Number** is a **required text field** on Shop Owner sign-up, stored on the vendor record (`ssmNumber`). It is **not** checked against any government database. Stronger verification (e.g., manual admin review) is future work, never an automatic database lookup.
   - **Shop QR code rotates every 60 seconds** (TOTP-style) so old screenshots can't be reused.
   - **Reward rules are set once by the shop owner** (the `VOUCHER` entity) on a "Reward Rules" page — a shop can have **many** rules, and can add, edit, or turn off any of them. The shop owner never manually creates a voucher per customer.
   - **Vouchers are created automatically** the moment a customer's points for a shop reach **any** threshold. If the customer qualifies for several, **all** show up in "My Vouchers" — never quietly pick one or discard the rest. The highest-value one is labeled **"Best Choice"**; the customer decides which to use.
   - **A voucher is tied to one shop** and only works there.
   - **A voucher can be redeemed exactly once.** After redemption it is marked used and disappears from the customer's active vouchers page.
   - **Redeeming a voucher costs points.** The reward rule's `pointsRequired` is deducted from the customer's balance **for that shop** when the voucher is redeemed, recorded as a `pointRedeemedAmount` ledger row. Points do not simply accumulate forever.
   - **Redemption has a manual fallback:** the shop owner can scan the voucher QR or **type the voucher code by hand**.
   - **On redemption, the app tells the shop owner what to give** (e.g., "Give: Free Cake") by looking up the reward rule. Shop owners never need to remember their own rules.
   - **Every check runs against the live database**, never against data stored on the phone.
7. **Ask before installing anything not listed in the tech table in Section 2.** If a new library or Firebase product is genuinely needed, propose it, explain why, and confirm before adding it to Gradle.
8. **Tunable numbers are config values, not hardcoded constants.** Points per shop visit, Spin the Wheel range, Memory Match reward, level thresholds, and voucher expiry length must live in one clearly named place (a constants/config class or database config node) so they can be changed without hunting through screens.
9. **Log points as a ledger.** Every points change is a row in the `POINT` entity (earned or redeemed, with its source). Never store a single overwritten "total" as the only truth — balances per shop are calculated from the ledger.
10. **The ERD and Flowchart may be improved — but propose first, never silently change.** If the AI sees a gap, conflict, or better design (a missing field, a wrong relationship, a flowchart step that doesn't match the app guide), it must stop, explain the issue and the suggested change in plain language, and wait for the developer's approval. Only after approval does it update the code **and** record the change in `docs/` (e.g., `docs/erd-changes.md`) so the documents and the app never drift apart.
11. **Use Android Studio's bundled JDK.** The developer has not installed a separate JDK. Do not tell them to install one. In Phase 1, help them confirm the project's Gradle JDK is set to the JDK bundled with Android Studio and that it is compatible with the Android Gradle Plugin version chosen — if there's a problem, explain it and ask before changing anything or installing anything.
12. **At the end of each phase**, summarize what was built, list any manual steps the developer still needs to do outside the IDE (e.g., "enable Email/Password sign-in in the Firebase console"), and confirm the Definition of Done before proceeding.

---

## 2. Tech Stack & Locked Versions

The tools below are the ones named in the Simple App Guide. No other tools are approved. **No library versions were specified in the source documents** — before installing anything, the AI must look up the current stable version of each item, tell the developer, and then **record the chosen version in this table** so it stays the source of truth.

| Part of the App  | Tool                                                     | Version                                                    | Notes                                                                                                     |
| ---------------- | -------------------------------------------------------- | ---------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| Platform         | Android                                                  | **minSdk = Android 8.0 (API 26)** or newer                 | Works on most phones from the last several years                                                          |
| App logic        | Kotlin                                                   | Built-in with AGP 9.4.0 (no separate Kotlin plugin needed) | Kotlin only — no new Java files                                                                           |
| JDK              | Bundled with Android Studio                              | JDK 17 (minimum required by AGP 9.x)                       | Used by Gradle to build; no separate JDK install                                                          |
| App layout       | XML                                                      | —                                                          | One layout file per screen/component                                                                      |
| Look and feel    | Google Material Design (Material Components for Android) | **1.14.0**                                                 | Rounded buttons, simple cards, familiar icons                                                             |
| Accounts & login | Firebase Auth                                            | **34.19.0 (via BoM)**                                      | Email + password. Handles password storage — never store passwords in the database                        |
| Live data        | Firebase Realtime Database                               | **34.19.0 (via BoM)**                                      | Stores users, vendors, points, vouchers, games, challenges; keeps all phones in sync                      |
| Notifications    | Firebase Cloud Messaging                                 | **34.19.0 (via BoM)**                                      | Level-ups, completed challenges, "reward ready"                                                           |
| QR scan          | ML Kit Barcode Scanning                                  | **17.3.0**                                                 | ⚠️ Replaces ZXing scanning — see `docs/erd-changes.md` Change 3. Actively maintained, works on Android 8+ |
| QR generate      | ZXing core                                               | **3.5.3**                                                  | Used only to draw QR Bitmaps into an ImageView — no camera, no deprecated APIs                            |
| Design mockups   | Figma                                                    | —                                                          | Every screen is built to match its Figma design                                                           |
| Code storage     | GitHub                                                   | —                                                          | Commit at the end of every phase at minimum                                                               |
| IDE              | Android Studio (already installed)                       | Latest stable (AGP 9.4.0, Gradle 9.6.0)                    | SDK, emulator, and `adb` come with it                                                                     |

**Developer tooling needed:**

- Android Studio (Android SDK, emulator, `adb`) — already installed
- An **Android Emulator** with a **Google Play** system image (Android 8.0+) as the main test device — the developer has no physical phone yet. Emulator camera scanning needs setup (webcam or VirtualScene), so use the debug helper below for early phases
- A **real Android phone** (8.0+, borrowed is fine) for a final test of camera scanning, focus, and notifications before Phase 7 is considered done
- A Firebase project (created manually in the Firebase console — AI should tell the developer when)
- **Two test accounts at all times** (one Customer, one Shop Owner) — the core loop needs both roles, so plan for two emulators, or one emulator switching accounts

⚠️ **Testing caveat:** Scanning needs a code on one screen and a camera on another. During early phases, the AI should provide a simple way to test without two devices (e.g., a temporary debug screen that displays the current shop code on the same device or lets you paste a code manually) and clearly mark it for removal before Phase 7.

---

## 3. Core Concepts the AI Must Not Confuse

### 3.1 The two QR codes

| Code                                          | Shown by                    | Scanned by | Purpose                                                                                                   |
| --------------------------------------------- | --------------------------- | ---------- | --------------------------------------------------------------------------------------------------------- |
| **Shop Code** (`QRCODE` entity)               | Shop Owner, on their screen | Customer   | Gives the customer points for visiting. Rotates every 60 seconds                                          |
| **Voucher Code** (`VOUCHER_USER.voucherCode`) | Customer, on their screen   | Shop Owner | Lets the customer claim a reward they've already unlocked. Scan it, or the shop owner types it in by hand |

### 3.2 Reward rule vs. voucher

- **Reward rule** = a `VOUCHER` record. Created once by the shop owner. Says "at N points, this reward" (`pointsRequired`, `voucherName`, `voucherDescription`).
- **Customer voucher** = a `VOUCHER_USER` record. Created **automatically by the app** for one customer from one rule, with its own unique `voucherCode`, `voucherStatus`, and `voucherExpiryDate`.

The naming in the ERD is easy to mix up — the AI should use "reward rule" in explanations and UI text, and keep `VOUCHER` / `VOUCHER_USER` only for data names.

### 3.3 Points are per shop

A customer's progress toward a reward is **that shop's points only**. Points earned at Shop A never count toward Shop B's rewards.

**Points are spent when a voucher is redeemed.** Redeeming a voucher deducts its `pointsRequired` from the customer's balance at that shop.

- **Balance at a shop** = sum of `pointEarnedAmount` − sum of `pointRedeemedAmount` for that customer + vendor. This decides which rewards the customer qualifies for.
- **Level** (Home Screen) is based on **lifetime points earned** across all shops (earned only, never reduced by spending), so a customer never loses a level just because they used a voucher.
- Redemption must re-check the balance **at scan time** — see Open Decision #7 for what happens when a customer holds several vouchers but can't afford all of them.

### 3.4 Data model (from the ERD)

The ERD is the starting source of truth for entities and field names (it is decisive but not final — see Rule 10 for how changes are proposed and recorded). Firebase Realtime Database is a JSON tree, not tables — the AI should map each entity to a top-level node keyed by its ID, keep the field names exactly as written, and use the ID fields as foreign keys.

| Entity           | Fields                                                                                                                   | Relationships                                                                                       |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| `USER`           | `userID` (PK), `userName`, `userEmail`, `role`                                                                           | One user has many points, games, vouchers, user-challenges. One user ↔ one vendor (for Shop Owners) |
| `VENDOR`         | `vendorID` (PK), `userID` (FK), `vendorName`, `vendorCategory`, `vendorAddress`, `ssmNumber`                             | Has many points, reward rules, QR codes                                                             |
| `QRCODE`         | `qrcodeID` (PK), `vendorID` (FK), `qrcodeValue`, `qrcodeGenerateDate`, `qrcodeExpiryDate`, `isActive`                    | Belongs to one vendor                                                                               |
| `POINT`          | `pointID` (PK), `userID` (FK), `vendorID` (FK), `pointEarnedAmount`, `pointRedeemedAmount`, `pointSource`                | Ledger row linking a user to a vendor                                                               |
| `VOUCHER`        | `voucherID` (PK), `vendorID` (FK), `voucherName`, `voucherDescription`, `pointsRequired`                                 | The reward rule. Belongs to one vendor                                                              |
| `VOUCHER_USER`   | `voucherUserID` (PK), `userID` (FK), `voucherID` (FK), `voucherCode`, `voucherStatus`, `voucherExpiryDate`               | A customer's own copy of a reward                                                                   |
| `GAME`           | `gameID` (PK), `userID` (FK), `gameType`, `gameAttempt`, `gamePointsRewarded`                                            | Records a game play by a user                                                                       |
| `CHALLENGE`      | `challengeID` (PK), `challengeTitle`, `challengeDescription`, `challengeTargetValue`, `challengeRewardPoint`, `isActive` | Defined once, used by many users                                                                    |
| `USER_CHALLENGE` | `userChallengeID` (PK), `userID` (FK), `challengeID` (FK), `currentValue`, `userChallengeStatus`                         | A user's progress on a challenge                                                                    |

**Passwords are not in `USER`** — Firebase Auth owns them.

---

## 4. Phase-by-Phase Plan

Each phase lists: **Goal**, **What the AI should build**, and **Definition of Done**. Tell the AI which phase to start on; it should not proceed past a phase's Definition of Done without your confirmation.

---

### Phase 1 — Foundations & Data Model

**Goal:** Android Studio project, Firebase project, and database structure are in place, with security rules that stop a buggy or malicious client from cheating.

**What the AI should build:**

- Ask the developer's OS, then confirm Android Studio, SDK, the Gradle JDK setting (Rule 11), and the emulator (Google Play system image, camera configured) are working
- Android Studio project (**Kotlin**, XML, minSdk 26) with Material Design theme, Firebase connected, and ZXing added — each dependency version recorded in Section 2
- `.gitignore` covering Firebase config and any secrets, plus a placeholder example file
- The Realtime Database structure from Section 3.4 (one node per entity, keyed by ID), documented in `docs/`
- A single **config class/node** for tunable numbers (see Rule 8), seeded with the starting values from Section 5
- **Firebase Realtime Database Security Rules** covering every node — at minimum:
  - customers can only read/write their own user record, points, games, and user-challenges
  - only a Shop Owner can create or edit reward rules for **their own** shop
  - a customer can never mark their own voucher as used
  - `role` cannot be changed after sign-up
- Basic app shell: package structure, a Splash/Welcome screen (logo + loading), and navigation skeleton

**Definition of Done:**

- [ ] Project builds with Android Studio's bundled JDK and runs on the Android emulator
- [ ] Firebase project created and connected (manual step — AI should tell you to do this)
- [ ] Database structure and Security Rules written, and AI has explained in plain language what a cheating client could and couldn't do
- [ ] Config values live in one place and are documented
- [ ] Section 2 version table is filled in

---

### Phase 2 — Accounts & Role-Based Sign-Up

**Goal:** Customers and Shop Owners can sign up and log in, and the app routes each to the right home screen automatically.

**What the AI should build:**

- Sign-up flow that first asks **"Are you signing up as a Customer or a Shop Owner?"**
- **Customer sign-up:** name, email, password — nothing extra
- **Shop Owner sign-up:** name, email, password, **plus SSM Registration Number** (required — the account cannot be created if blank). Stored as `ssmNumber` on the `VENDOR` record along with the shop details. **No external verification of any kind**
- Firebase Auth email/password sign-up and login; on sign-up, write the matching `USER` record (with `role`) and, for Shop Owners, the `VENDOR` record
- Login screen with no role choice — the app reads `role` from the account and opens either the Customer Home or the Shop Home (placeholder screens are fine for now)
- Sensible input validation and readable error messages (wrong password, email already used, empty SSM field)

**Definition of Done:**

- [ ] You can sign up as a Customer and as a Shop Owner and both records appear in the database
- [ ] Shop Owner sign-up is blocked when the SSM field is empty
- [ ] Logging in lands each role on the correct placeholder home screen without asking for a role
- [ ] AI has explained how `role` is stored and why it can't be edited afterward

---

### Phase 3 — Customer Side, Core Loop

**Goal:** A customer can see their progress, browse shops, and earn points by scanning a shop code.

**What the AI should build:**

- **Customer Home Screen:** shows points, level, and a progress bar. Level thresholds come from config (Section 5)
- **Find Shops:** a list of registered vendors, showing name, category, address, and the customer's progress at each
- **Scan screen** (ZXing camera): reads the shop QR, validates it against the live database (real, active, not expired, belongs to a registered vendor), then writes a `POINT` ledger row with `pointSource` set to shop visit
- "+N points!" animation and progress bar fill on success
- Clear errors for: invalid code, expired code, camera permission denied, and no internet
- Debug helper for testing without a second device (see Section 2 caveat)

**Definition of Done:**

- [ ] Customer Home shows accurate points and level, calculated from the `POINT` ledger
- [ ] Find Shops lists every registered vendor
- [ ] Scanning a valid shop code adds the configured points and the animation plays
- [ ] Invalid and expired codes are rejected with a clear message
- [ ] AI has explained how the scan is validated and why it checks the database instead of trusting the phone

---

### Phase 4 — Shop Owner Side, Core Loop

**Goal:** A shop owner can show a rotating QR, define reward rules, and redeem vouchers.

**What the AI should build:**

- **Shop Home Screen** for Shop Owners
- **Show QR Code screen:** generates a QR with ZXing whose value changes every 60 seconds (TOTP-style) and records it in `QRCODE` (`qrcodeValue`, `qrcodeGenerateDate`, `qrcodeExpiryDate`, `isActive`). The customer-side validation from Phase 3 must accept only the current code
- **Reward Rules page:** add, edit, and turn off multiple rules (`voucherName`, `voucherDescription`, `pointsRequired`) — e.g., 100 → 10% Discount, 200 → Free Cookie, 350 → Free Meal. Turning a rule off must not delete vouchers customers already earned from it (see Open Decision on this)
- **Scan to Redeem screen:** camera scan **plus manual code entry** as backup. On a valid voucher, show the shop owner exactly what to give (e.g., "Give: Free Cake") using the linked reward rule, then mark the voucher **used**
- Redemption checks, all against the live database: the code is real, it belongs to **this** shop, it hasn't already been used, hasn't expired, and the customer's **current balance at this shop covers the reward's `pointsRequired`**. On success, mark the voucher used **and** write the `pointRedeemedAmount` ledger row in one atomic step so a half-finished redemption can't happen

**Definition of Done:**

- [ ] Shop QR visibly changes every 60 seconds, and an old screenshot no longer earns points
- [ ] A shop owner can create multiple rules, edit them, and turn one off
- [ ] Scanning a voucher from a different shop is rejected
- [ ] Manual code entry works exactly like scanning
- [ ] Redeeming a voucher deducts its points from that customer's balance at that shop
- [ ] AI has explained how the 60-second rotation is generated and validated

---

### Phase 5 — Rewards & Vouchers

**Goal:** Vouchers appear automatically when points cross a threshold, and customers can see and use them.

**What the AI should build:**

- **Auto-voucher logic:** whenever a customer's points at a shop change, compare their balance for that shop against **every active** reward rule of that shop. For each threshold reached, create a `VOUCHER_USER` record with a unique `voucherCode`, status active, and `voucherExpiryDate` from config — **unless the customer already holds an active (unused, unexpired) voucher for that same rule**. **No duplicates** if the check runs twice. Creating a voucher does **not** deduct points; points are deducted only at redemption
- **Cost on redemption:** when a voucher is redeemed, deduct `pointsRequired` from that shop's balance via a `pointRedeemedAmount` ledger row. After that, re-check the customer's remaining vouchers at that shop against the new balance (behavior per Open Decision #7), and show the updated balance on Home and Find Shops
- **My Vouchers screen:** vouchers grouped by shop, each with its own QR code (ZXing) and readable code text. Label the highest-value qualifying voucher at each shop **"Best Choice"**. Do not hide the others
- After a voucher is used, it disappears from the customer's active vouchers page
- Handling for expired vouchers (per Section 5 decisions)
- Security Rules updated so a customer cannot forge or change a voucher

**Definition of Done:**

- [ ] Reaching a threshold creates exactly one voucher for it
- [ ] Crossing two thresholds at once creates both, and both show in My Vouchers with the highest labeled "Best Choice"
- [ ] A used voucher is gone from My Vouchers and cannot be redeemed again
- [ ] Redeeming a voucher reduces that shop's balance by exactly its `pointsRequired`, and earning points again can issue new vouchers
- [ ] Holding several vouchers but only enough points for some behaves as agreed in Open Decision #7
- [ ] Voucher from Shop A is rejected at Shop B
- [ ] AI has explained where auto-creation runs and what stops a customer from faking it

---

### Phase 6 — Games, Levels & Challenges

**Goal:** The engagement features work and feed the same points ledger.

**What the AI should build:**

- **Level-up feel:** progress bar fill and a level-up moment when a threshold is crossed
- **Spin the Wheel:** once per day, random points within the configured range. Enforce the daily limit against the database, not the phone's clock or local storage. Record in `GAME` and the `POINT` ledger
- **Memory Match:** card-flipping game using shop logos; matching pairs earn points. Record in `GAME` and the `POINT` ledger
- **Challenges list:** shows active challenges and the customer's progress (`CHALLENGE` + `USER_CHALLENGE`), e.g., "Visit 5 shops this week." Completing one awards `challengeRewardPoint`
- **Push notifications** (Firebase Cloud Messaging) for level-ups and completed challenges, and "your reward is ready" for new vouchers — the AI must propose how these are triggered and confirm before building (see Open Decisions)
- **Shop Settings page:** shop owner edits shop name, category, address, and logo
- **Shop Owner statistics** (from the Vendor flowchart): shop visit counts and how many vouchers customers redeemed per day

**Definition of Done:**

- [x] Spin the Wheel can be used once per day and the limit can't be bypassed by reinstalling or changing the phone clock
- [x] Memory Match awards points and records the game
- [ ] A challenge completes and awards its points exactly once
- [ ] Notifications arrive for the agreed events
- [ ] Shop owner can edit shop details and see daily visit and redemption numbers
- [ ] AI has explained how daily limits and challenge progress are tracked

---

### Phase 7 — Polish & Testing

**Goal:** Edge cases behave sensibly and the app matches the designs.

**What the AI should build/help with:**

- Remove all debug helpers from earlier phases
- Test and fix: expired shop codes, already-used vouchers, wrong-shop vouchers, empty/invalid manual code entry, camera permission denied, and **no internet connection** (clear message, no crash, no half-written data)
- Compare every screen against its Figma design and fix mismatches
- Re-check Security Rules with the developer: walk through each cheat scenario listed in Section 1 Rule 6 and confirm it fails
- Bug fixes and smoothing (loading states, empty states like "No vouchers yet")

**Definition of Done:**

- [ ] Every edge case above has been tried on the emulator, and camera scanning plus notifications have been tried on at least one real phone, and handled
- [ ] No debug screens or hardcoded test codes remain
- [ ] Screens match Figma
- [ ] You can demonstrate the full loop end to end: sign up → scan shop code → auto-voucher → redeem → voucher gone and points deducted

---

## 5. Open Decisions & Source Conflicts

The three source documents disagree in places or leave things open. The AI must **use the default below, tell the developer it did so, and make it a config value where it's a number.** The developer can change any of these — update this table when they do.

| #   | Topic                                             | What the sources say                                                                                                                                                                                                   | Default the AI uses until told otherwise                                                                                                                                                                                                                                                                                                  |
| --- | ------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | Points per shop visit                             | Flowchart: 10. Simple Guide: "e.g. 20"                                                                                                                                                                                 | **10** (config value)                                                                                                                                                                                                                                                                                                                     |
| 2   | Spin the Wheel / Memory Match points              | Not specified                                                                                                                                                                                                          | AI proposes a range; developer confirms in Phase 6                                                                                                                                                                                                                                                                                        |
| 3   | Voucher code type                                 | Flowchart: TOTP QR for voucher. Simple Guide: unique voucher QR + must be typeable by hand. ERD: single stored `voucherCode`                                                                                           | **Static unique `voucherCode`** (a rotating code can't be typed in from memory). Used-once + shop-match checks provide the protection                                                                                                                                                                                                     |
| 4   | How vouchers are created                          | Flowchart: vendor "Create Voucher". Simple Guide: vendor sets rules once, app auto-creates customer vouchers                                                                                                           | **Simple Guide wins:** vendor creates reward rules; app creates customer vouchers                                                                                                                                                                                                                                                         |
| 5   | Where auto-voucher creation runs                  | Not specified. Client-only creation is easy to cheat; server-side logic (e.g., Cloud Functions) is not in the approved tool list                                                                                       | **✅ CONFIRMED: Client-side** (Android app checks thresholds after writing a `POINT` row and creates `VOUCHER_USER` records). Security Rules prevent customers from writing arbitrary vouchers. Cloud Functions stays out of scope.                                                                                                       |
| 6   | Notification triggers                             | FCM is listed, but sending targeted pushes safely usually needs server-side code                                                                                                                                       | AI proposes an approach (e.g., local notifications vs FCM) and confirms in Phase 6                                                                                                                                                                                                                                                        |
| 7   | Several vouchers, not enough points for all       | **Confirmed:** points are spent on redemption. Simple Guide also says all qualifying vouchers stay visible — these two rules collide (e.g., 350 points shows the 100/200/350 vouchers, but redeeming the 350 leaves 0) | **Vouchers stay visible** but a voucher can only be redeemed if the customer's balance at that shop covers its `pointsRequired` at scan time. Vouchers they can no longer afford show as **locked** (not deleted) and unlock again when they earn enough points. **AI must ask the developer to confirm this in Phase 5 before building** |
| 8   | Voucher expiry length                             | Not specified (ERD has `voucherExpiryDate`)                                                                                                                                                                            | Config value; AI asks for the number                                                                                                                                                                                                                                                                                                      |
| 9   | Shop owner approval                               | Simple Guide: open question                                                                                                                                                                                            | **Sign up freely** (no approval). Manual admin review is future work                                                                                                                                                                                                                                                                      |
| 10  | Must a shop have a reward rule before going live? | Open question                                                                                                                                                                                                          | **No** — shops can start with none                                                                                                                                                                                                                                                                                                        |
| 11  | Turning off a rule                                | Simple Guide: rules can be turned off. ERD `VOUCHER` has no `isActive` field                                                                                                                                           | Add an `isActive` field to `VOUCHER` (note this ERD deviation in `docs/`). Already-earned vouchers stay valid                                                                                                                                                                                                                             |
| 12  | Level thresholds                                  | Levels exist but no formula given; ERD has no level field                                                                                                                                                              | **Derive level from lifetime points earned** (not balance, since points are now spent), thresholds in config; don't store level                                                                                                                                                                                                           |
| 13  | Points counter meaning                            | ERD `POINT` has earned/redeemed amounts per user per vendor                                                                                                                                                            | Per-shop balance = sum earned − sum redeemed for that vendor (now actively used, since redemption costs points)                                                                                                                                                                                                                           |
| 14  | Voucher cost recorded where                       | ERD has `pointsRequired` on the rule but no cost on `VOUCHER_USER`                                                                                                                                                     | Cost is read from the linked rule at redemption. **AI should propose** copying the cost onto `VOUCHER_USER` at creation, so editing a rule later can't change what an already-issued voucher costs (an ERD change — follow Rule 10)                                                                                                       |

---

## 6. Quick Reference — Starting a Session

At the start of any coding session, tell your AI assistant:

> "Read SAPOTLOKAL_BUILD_GUIDE.md. We're on Phase [N]. [Any specific thing you want to work on today]."

If you've made a decision that changes something in this file (e.g., you set voucher expiry to 30 days, or approved an ERD change the AI proposed), ask the AI to update this file so it stays the source of truth for future sessions.
