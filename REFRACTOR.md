# SapotLokal Refactor Guide

The plan for rebuilding SapotLokal's rewards system and UI. Work through the phases in order. Each phase ends with a "Done when" checklist. Don't start the next phase until it's ticked.

Companion file: **[BRAND_DESIGN.md](BRAND_DESIGN.md)**, the design and copy rules every screen must follow.

---

## 1. Where we are vs where we're going

### 1.1 How it works today

| Area | Today |
|---|---|
| Currency | **One shared number.** A shop visit writes a `/points` row for that shop *and* adds the same amount to `users/{uid}/lifetimePoints`. Games and missions also write `/points` rows (with an empty `vendorID`) and add to `lifetimePoints`. |
| Level | `lifetimePoints` sets one of 5 tiers (Freshie → Sapot GOAT, thresholds 0 / 200 / 600 / 1500 / 2500) with multipliers 1.0–2.0x. |
| Multiplier | Applied to **everything**: shop visits, games and mission rewards. |
| Shop visit | Hardcoded `AppConfig.POINTS_PER_SHOP_VISIT = 10`, plus a +20 first-visit bonus and a +20 weekly-streak bonus. 12-hour cooldown per shop. |
| Missions | 6 weekly missions in `MissionRepository` (Tapah Explorer, Loyal Supporter, Weekend Warrior, Daily Tri-Play, Night Owl, Arcade Champion). Tap to claim. Rewards are points, multiplied. No lifetime missions. |
| Badges | 8 in `BadgeDef`, unlocked as side effects of missions. Stored as `unlockedBadges` booleans. No XP. Profile greys out locked ones and opens a details dialog. |
| Games | Spin the Wheel (5–50, once a day), Memory Match (20 per win, 60/day cap). Rewards are points, multiplied. |
| Leaderboard | Ranks by **points earned** this week, plus an all-time board. No rewards. |
| UI | Customer screens use the new `hu_*` look. Shop-owner and sign-in screens still use the old `sapot_*` cocoa/blue look. Two separate login screens. |
| Repo | Carries a Figma Make web prototype, Impeccable design tooling, and about 4,000 lines of old reports and guides. |

### 1.2 Where we're going

| Area | Target |
|---|---|
| Currency | **Two separate things.** **Points** belong to one shop and are spent on rewards. **XP** belongs to the customer, is never spent, and decides their rank. |
| Shop points | The shop owner picks points per visit (5–50, default 10). The customer gets `points per visit × rank bonus`. No other shop bonuses. |
| XP sources | Shop visits (flat), games, weekly missions, lifetime missions, badges, weekly top 3. |
| Ranks | 5 ranks. Each gives a bonus on shop points only (1x → 3x). |
| Missions | 3 weekly (reset every Monday) + 3 lifetime. Tap to claim XP. |
| Badges | 8 badges on the Me screen, grey until earned. Tap opens a sheet that says how to earn it. Tap to claim XP. |
| Games | Same two games, same gameplay. They give XP instead of points. |
| Leaderboard | Weekly, ranked by **number of visits**. Top 3 claim XP after the week ends. |
| UI | Every screen follows BRAND_DESIGN.md. One login screen. |
| Repo | Android app, Firebase config, seed tool and these two guides. Nothing else. |

---

## 2. Decisions

### 2.1 Confirmed with you

| # | Decision |
|---|---|
| D1 | Shop visits give a small flat XP (not multiplied). |
| D2 | Rank bonus ladder: **1x, 1.25x, 1.5x, 2x, 3x**. It applies to shop points only. |
| D3 | New rank names (proposed in §3.3; change them before Phase 4 if you want). |
| D4 | Mission and badge XP is **tap to claim**. |
| D5 | Leaderboard counts **every valid scan** (the 12-hour per-shop cooldown stays). |
| D6 | Weekly top 3 **claim their XP in the app** (no server, works on the free Spark plan). |
| D7 | Shop owner picks points per visit from **5, 10, 15, 20, 25, 30, 40, 50** (default 10). |
| D8 | First-visit and weekly-streak shop bonuses are **removed**. |
| D9 | Delete: Figma/Impeccable tooling, the web prototype, old docs, legacy code. |
| D10 | Keep **English + Malay**. |
| D11 | **Wipe and reseed** the database (no migration). |

### 2.2 Decisions I made (say if you disagree)

| # | Decision | Why |
|---|---|---|
| M1 | **One login screen** for everyone. After sign-in, the app sends you to the customer or shop-owner home based on your role. The role picker only appears on sign-up. | Two login screens is a duplicate entry point (BRAND_DESIGN §7). |
| M2 | **Badges live in Me only.** Play holds games, missions and the leaderboard. | One destination, one entry point. |
| M3 | **The all-time leaderboard is removed.** Only "This week" remains. | You asked for a weekly visit race. An all-time board can't be won by new customers, which hurts retention. Rank already shows long-term progress. |
| M4 | **XP never goes down and ranks never drop.** | Losing a rank feels like a punishment. Retention comes from weekly missions and the weekly race instead. |
| M5 | **Mission, badge and leaderboard counts come from small counter nodes** (`/userStats`, `/weeklyVisits`), not from re-reading the whole `/points` ledger. | It's faster, and customers no longer need read access to everyone's points. |
| M6 | **Rank names stay in Malay in both languages,** like the "Selamat pagi" greeting. | They're names, not sentences. |
| M7 | **A top-3 reward can be claimed for 4 weeks** after the week ends. | A customer who misses one app-open shouldn't lose their prize. |
| M8 | **Kopi cups count visits at the customer's current rate** (see BRAND_DESIGN §6 "Shop card"). | Points per visit now varies by shop and rank, so a fixed 10-points-per-cup no longer works. |

---

## 3. The economy

Every tunable number lives in **`AppConfig.kt`** (rule: never hardcode these in a screen). The tables below are the starting values.

### 3.1 XP sources

| Source | XP | Limit |
|---|---|---|
| Shop visit (valid scan) | **15** | 12-hour cooldown per shop (unchanged) |
| Spin the Wheel | **2, 3, 4, 5, 6, 8, 10 or 15** (one segment at random) | Once a day |
| Memory Match win | **5** | First 2 wins a day give XP; after that it's play for fun |
| Weekly missions | 30–40 each (§3.4) | Once per mission per week |
| Lifetime missions | 250–300 each (§3.4) | Once ever |
| Badges | 25–100 each (§3.5) | Once ever |
| Weekly leaderboard | **#1 150 · #2 100 · #3 75** | Once per week, claim within 4 weeks |

Games are capped at about **25 XP a day** so visiting shops, which is what the app exists for, always stays the main way to rank up.

### 3.2 Expected weekly XP

| Customer | Behaviour | XP / week |
|---|---|---|
| Casual | 2 visits, games on 2 days, 1–2 weekly missions | ~95 |
| Regular | 4 visits, games on 4 days, all 3 weekly missions | ~220 |
| Keen | 8 visits, games every day, all missions, sometimes top 3 | ~390 |

On top of that, one-off XP is available: badges (425 total) and lifetime missions (850 total). Most of it is earned in the first two months.

### 3.3 Ranks

| Rank | Name | Meaning | XP needed | Shop points bonus | Regular reaches it in | Casual reaches it in |
|---|---|---|---|---|---|---|
| 1 | **Jiran Baru** | New neighbour | 0 | 1x | day 1 | day 1 |
| 2 | **Pelanggan Setia** | Loyal customer | 400 | 1.25x | ~2 weeks | ~4 weeks |
| 3 | **Kaki Lokal** | Local regular | 1,500 | 1.5x | ~6 weeks | ~3 months |
| 4 | **Otai Lokal** | Local veteran | 3,500 | 2x | ~3 months | ~7 months |
| 5 | **Legenda Sapot** | Sapot legend | 7,000 | 3x | ~6 months | over a year |

**Why this is the sweet spot:**
- **Rank 2 comes fast** (first fortnight). Early wins hook new customers.
- **The middle ranks are steady** (every 1–2 months for a regular). There's always a next goal in sight.
- **Rank 5 takes about 6 months** of regular visits. It's a real status, and the 3x bonus stays rare enough that shop owners can afford it.

**Rounding:** shop points = `round(pointsPerVisit × bonus)`, rounding halves up. For example 10 × 1.25 = 12.5 → 13, and 5 × 1.25 = 6.25 → 6.

### 3.4 Missions

**This week** (resets Monday 00:00, Asia/Kuala_Lumpur, using the existing `MissionDateUtils`):

| ID | Name (EN / MS) | Goal | XP |
|---|---|---|---|
| `w_visits` | Make 3 visits / Buat 3 lawatan | 3 valid scans this week | 40 |
| `w_explore` | Try 2 different shops / Cuba 2 kedai berbeza | 2 distinct shops this week | 30 |
| `w_play` | Play on 3 days / Main 3 hari | Any game played on 3 different days this week | 30 |

**Lifetime:**

| ID | Name (EN / MS) | Goal | XP |
|---|---|---|---|
| `l_visits` | Make 50 visits / Buat 50 lawatan | 50 valid scans in total | 300 |
| `l_shops` | Visit 10 different shops / Lawat 10 kedai berbeza | 10 distinct shops in total | 300 |
| `l_weeks` | Finish every weekly mission 4 times / Siapkan semua misi mingguan 4 kali | 4 weeks where all 3 weekly missions were claimed | 250 |

### 3.5 Badges

| ID | Name | How to earn it (this exact line is shown in the badge sheet) | XP |
|---|---|---|---|
| `langkah_kanan` | Langkah Kanan | Make your first visit | 25 |
| `regular` | Regular | Visit the same shop on 3 days in one week | 50 |
| `hantu_malam` | Hantu Malam | Visit a shop after 8 PM | 50 |
| `combo` | Combo | Visit a shop, spin the wheel and win Memory Match on the same day | 50 |
| `nasib_baik` | Nasib Baik | Land on the top prize on the wheel (15 XP after Phase 7) | 50 |
| `otak_cerdas` | Otak Cerdas | Win Memory Match without a wrong pair | 50 |
| `belanja_diri` | Belanja Diri | Use your first voucher | 50 |
| `juara_minggu` | Juara Minggu | Finish in the weekly top 3 | 100 |

Badges and missions never reward the same action (for example, "10 shops" is a mission, so it isn't also a badge).

### 3.6 Leaderboard

- Counts **valid scans from Monday 00:00 to Sunday 23:59** (Malaysia time).
- Order: most visits first; on a tie, whoever reached that count first.
- A customer needs at least 1 visit to appear.
- After the week ends, the top 3 see a card on Home and Play: "You came #2 last week" with "Claim 100 XP". Claiming also unlocks the `juara_minggu` badge.

---

## 4. Data model (Realtime Database)

Wipe the database and reseed it with this shape.

```
users/{uid}
  userID, userName, userEmail, role ("customer" | "vendor"), avatar
  xp: Int                      // customers only, never decreases
  rank: Int                    // 1–5, cached from xp for the leaderboard and display
  fcmToken

vendors/{vendorID}             // vendorID == owner uid
  ...existing fields...
  pointsPerVisit: Int          // one of 5,10,15,20,25,30,40,50; default 10

points/{pointID}               // SHOP POINTS ONLY. vendorID is always set.
  pointID, userID, vendorID, timestamp
  pointSource: "shop_visit" | "redemption"
  pointEarnedAmount, pointRedeemedAmount
  basePoints, rankBonus        // snapshot: what the shop gave and the bonus applied

xpEvents/{uid}/{eventKey}      // XP LEDGER. eventKey is deterministic, so it can only be written once.
  amount, source, timestamp
  // eventKey examples:
  //   visit_{pointID}            game_{gameID}
  //   wmission_{missionId}_{weekKey}   lmission_{missionId}
  //   badge_{badgeId}            leaderboard_{weekKey}

userStats/{uid}                // counters for missions and badges, updated in the same write as the event
  totalVisits, shopsVisited/{vendorID}: true, weeksAllMissions
  vouchersUsed, lastVisitAt

weeklyVisits/{weekKey}/{uid}   // leaderboard source
  visits, lastVisitAt, userName, avatar, rank

missionProgress/{uid}/{progressKey}   // progressKey = {missionId}_{weekKey} or {missionId}
  progress, completedAt, claimedAt

badges/{uid}/{badgeId}
  unlockedAt, claimedAt

games/{gameID}                 // unchanged shape; gamePointsRewarded renamed gameXpRewarded

vouchers/{voucherID}           // shop rewards (unchanged)
voucherUsers/{voucherUserID}   // claimed vouchers (unchanged)
qrcodes/{qrcodeID}             // unchanged
shopNotifications/{id}         // unchanged
```

**Removed:** `users/*/lifetimePoints`, `users/*/currentTier`, `users/*/unlockedBadges`, the old `/userMissions` and missions catalogue node, `/points` rows with an empty `vendorID`, `first_visit_bonus` / `streak_bonus` / `spin_wheel` / `memory_match` / `challenge` point sources.

### 4.1 One scan, one atomic write

A valid scan writes all of these in **one** multi-path `updateChildren`:
1. `points/{pointID}`: shop points (`round(pointsPerVisit × bonus)`).
2. `xpEvents/{uid}/visit_{pointID}`: 15 XP.
3. `users/{uid}/xp`: `ServerValue.increment(15)`. `rank` is recomputed if a threshold was crossed.
4. `userStats/{uid}/...`: counters.
5. `weeklyVisits/{weekKey}/{uid}/visits`: `ServerValue.increment(1)`.
6. Mission progress for `w_visits`, `w_explore`, `l_visits`, `l_shops`, and badge unlocks that this scan triggers (`unlockedAt` only; the XP comes when the customer claims it).

Games and claims follow the same pattern: event row + XP increment + counters in one write.

### 4.2 Security rules (`docs/firebase-rules.json`)

Without a server, a determined customer can still fake small amounts of XP. The rules make that hard, bounded and visible:

- `xpEvents/{uid}/{eventKey}`: only the owner can write, only once (`!data.exists()`), and the amount is capped by key prefix: `visit_` ≤ 15, `game_` ≤ 15, `wmission_` ≤ 40, `lmission_` ≤ 300, `badge_` ≤ 100, `leaderboard_` ≤ 150.
- `users/{uid}/xp`: owner only, never decreases, and each write may raise it by at most 300.
- `points` (shop_visit): `vendorID` must exist, and `pointEarnedAmount ≤ vendors/{vendorID}/pointsPerVisit × 3`.
- `vendors/{id}/pointsPerVisit`: owner only, and it must be one of the allowed values.
- `weeklyVisits/{weekKey}/{uid}/visits`: owner only, +1 per write.
- `badges/{uid}`, `missionProgress/{uid}`, `userStats/{uid}`: owner only.
- Readable by any signed-in user: `weeklyVisits`, plus the public profile fields used on the leaderboard. Customers can no longer read the whole `/points` node.

**Future (Blaze plan):** move awarding into Cloud Functions so the client can't award itself anything. Not part of this refactor.

---

## 5. Phases

### Phase 0: Clean the repo

Start fresh. Delete first, then make sure it still builds.

**Delete:**
- `.figma/`, `.impeccable/`, `.claude/agents/impeccable-*.md`
- Web prototype: `src/`, `public/` (fonts and images only feed the web mock; the Android fonts live in `app/src/main/res/font`), `index.html`, `package.json`, `pnpm-lock.yaml`, `vite.config.ts`, `tsconfig.json`, `.mise.toml`
- `AGENTS.md`, `DESIGN.md`, `PRODUCT.md`, `SAPOTLOKAL_BUILD_GUIDE.md`
- `docs/*.md` (all of them), `docs/*.drawio`, `docs/SapotLokal_ERD.jpg`

**Keep:** `docs/firebase-rules.json` (to be rewritten in Phase 2), `docs/licenses/` (the Figtree licence must ship), `functions/`, `tools/seed/`, `firebase.json`, `.firebaserc`, Gradle files, `app/`.

**Rewrite `CLAUDE.md`** as short Android project instructions: the stack (Kotlin, XML views, Firebase RTDB/Auth/FCM), how to build and run on a device, and "follow REFRACTOR.md and BRAND_DESIGN.md". Remove the `@AGENTS.md` import.

**Update `.gitignore`:** drop the web/Node entries that no longer apply. Keep the Firebase secrets, `serviceAccountKey*.json` and `google-services.json` entries.

**Legacy code** (remove as the later phases replace it; list it now so nothing is forgotten):
- `sapot_*` colours and styles in `colors.xml`, `styles.xml`, `themes.xml`, and the theme mappings that point at them.
- `AppConfig`: `POINTS_PER_SHOP_VISIT`, `FIRST_VISIT_BONUS_POINTS`, `WEEKLY_STREAK_*`, `CustomerTier`, `LEVEL_THRESHOLDS`, `MEMORY_MATCH_REWARD_POINTS` and the other old point-based game values.
- `PointSource` bonus, game and challenge constants. `PointRepository.addPoints` applying the multiplier to everything. `claimMissionReward`, `awardMiniGamePoints`, `newGamePointEntry`.
- `MissionRepository.seedDefaultMissions`, the old 6 missions, and `UserMission.fromSnapshot`'s old-spelling reader.
- `BadgeRepository` booleans in `unlockedBadges`, and the old `BadgeDef` entries.
- `LeaderboardRepository.fetchAllTimeLeaderboard`.
- `VendorLoginActivity` and its layout (merged into `LoginActivity`, M1).
- Unused resources: run **Analyze → Run Inspection by Name → Unused resources** and delete what it finds. Merge the 9 string files into four: `strings_common.xml`, `strings_customer.xml`, `strings_owner.xml`, `strings_auth.xml` (same four in `values-ms`).

**Done when:**
- [ ] Only the Android app, Firebase config, `functions/`, `tools/seed/`, `docs/firebase-rules.json`, `docs/licenses/`, `CLAUDE.md`, `REFRACTOR.md` and `BRAND_DESIGN.md` remain at the top level.
- [ ] `./gradlew :app:assembleDebug` passes and the app still runs on the phone.

### Phase 1: Design foundations

- Make `Theme.SapotLokal` (`hu_*` tokens, Figtree, BRAND_DESIGN §3–§5) the **only** app theme. Point every activity in `AndroidManifest.xml` at it.
- Build shared styles for the components in BRAND_DESIGN §6: buttons (main, outlined, destructive, text link), the input field with focus and error states, chip, segment, card, tile, row, bottom sheet, ticked progress bar.
- Build the shop-owner bottom bar (Home · Rewards · Show QR · Redeem · Shop) as a twin of the customer bar.
- Do a copy pass on all strings against BRAND_DESIGN §2: glossary words, no repeated subheadings, no jargon, EN + MS.

**Done when:**
- [ ] No layout or style references a `sapot_*` resource.
- [ ] Every string uses the glossary words, and the Malay file has the same keys as the English one.

### Phase 2: Data, rules, seed

- New and changed models in `data/model/`: `User` (xp, rank), `Vendor.pointsPerVisit`, `Point` (basePoints, rankBonus), `XpEvent`, `UserStats`, `WeeklyVisits`, `MissionProgress`, `BadgeProgress`.
- `AppConfig`: `Rank` enum (§3.3), XP values (§3.1), `ALLOWED_POINTS_PER_VISIT`, `DEFAULT_POINTS_PER_VISIT = 10`, `VISIT_XP = 15`, game XP values, leaderboard XP, `LEADERBOARD_CLAIM_WEEKS = 4`.
- Rewrite `docs/firebase-rules.json` (§4.2) and deploy it: `npx firebase-tools deploy --only database`.
- Update `tools/seed/seed.js` and `seed.json`: shops get a mix of `pointsPerVisit` values; customers get xp spread across all 5 ranks; and there's a `weeklyVisits` race for this week and last week, with one seed customer in last week's top 3 holding an unclaimed reward.
- Wipe and reseed: `npm run cleanup -- --yes`, delete any leftover non-seed data in the console, then `npm run seed -- --yes`.
- Write `docs/DATABASE.md`, a one-page description of §4 as built.

**Done when:**
- [ ] Rules deploy without errors.
- [ ] The seed runs cleanly and the console shows the new node shapes.
- [ ] Unit tests cover `Rank.fromXp`, rounding of `pointsPerVisit × bonus`, and week keys.

### Phase 3: Shop points

- **Shop settings:** a "Points per visit" picker (chips: 5 · 10 · 15 · 20 · 25 · 30 · 40 · 50). Saving writes `vendors/{id}/pointsPerVisit`.
- **Scan** (`ScanActivity` → `PointRepository.recordShopVisit`): read the shop's `pointsPerVisit` and the customer's rank, then write §4.1 in one update. Remove the bonuses (D8).
- **Scan result:** "+15 points at Kopi Kita" and "+15 XP" as two lines. When a bonus applied: "+15 points at Kopi Kita (1.5x)".
- **Shop page and shop card:** "10 points a visit", and "15 for you" when the customer's bonus is above 1x. Cups follow M8.
- **Rewards and redemption:** no logic change. Check that `VoucherRepository` only counts rows with this shop's `vendorID`.

**Done when:**
- [ ] A shop set to 20 points gives a rank-3 customer 30 points.
- [ ] No shop-visit or bonus row is written without a `vendorID`. (Game and mission rows move to XP in Phases 5 and 7.)
- [ ] Changing points per visit doesn't change any balance that was already earned.

### Phase 4: XP and ranks

- `XpRepository`: `award(uid, eventKey, amount, extraUpdates)` writes the event, the XP increment, the rank update when a threshold is crossed, and any extra paths in one update. Every XP source goes through it.
- **Rank-up sheet:** shown once per new rank: rank badge, "You're now Kaki Lokal", "1.5x points at every shop", one button "Nice".
- **Rank card** (BRAND_DESIGN §6) on Home, Play and Me.

**Done when:**
- [ ] XP is only ever written through `XpRepository`.
- [ ] Crossing 400 XP shows the rank-up sheet once, and the next scan pays 1.25x.

### Phase 5: Missions

- Replace `MissionRepository` and `WeeklyMission` with `MissionDef` (§3.4: id, type weekly/lifetime, target, xp, how to measure) and progress read from `userStats`, `weeklyVisits` and `missionProgress`.
- Progress is updated inside the scan and game writes (§4.1), not by a later sync pass.
- **Missions screen** (from Play; replaces `ChallengesActivity`): two sections, "This week" with "Resets in 3 days" on the right, then "Lifetime". Mission rows per BRAND_DESIGN §6. Tap "Claim 40 XP" → `XpRepository.award`.
- `l_weeks`: when the third weekly mission of a week is claimed, increment `userStats/weeksAllMissions`.

**Done when:**
- [ ] On Monday 00:00 Malaysia time the weekly missions show 0 progress, and last week's unclaimed missions are no longer claimable.
- [ ] Each lifetime mission can be claimed once ever.

### Phase 6: Badges

- `BadgeDef` gets the 8 badges in §3.5 (id, names in EN/MS, how-to line in EN/MS, xp, icon, optional target for a progress count).
- Unlock checks run inside the writes that can trigger them: scan (`langkah_kanan`, `regular`, `hantu_malam`, `combo`), games (`nasib_baik`, `otak_cerdas`, `combo`), voucher use (`belanja_diri`, unlocked by the customer's own app when it sees a used voucher, since the shop owner can't write a customer's badges), leaderboard claim (`juara_minggu`).
- **Me screen:** a "Badges" section with a 4-column grid (3 columns under 360dp), styled per BRAND_DESIGN §6. Tap → badge sheet. "Claim 50 XP" → `XpRepository.award` and sets `claimedAt`.
- **Unlocked toast:** "Badge unlocked: Hantu Malam" with a "See" action that opens the sheet.
- New badge art for the renamed or new badges (`otak_cerdas`, `belanja_diri`, `juara_minggu`) in the line-icon style.

**Done when:**
- [ ] Every locked badge opens a sheet that says exactly how to earn it.
- [ ] Each badge pays XP once.

### Phase 7: Games

- Gameplay, screens and the once-a-day / daily-cap checks stay the same.
- Spin wheel segments become the XP values in §3.1; Memory Match pays 5 XP for the first 2 wins a day.
- Rewards go through `XpRepository.award` with `game_{gameID}`. No `/points` rows.
- Copy: "+8 XP", "Come back tomorrow", "Played today: 2 of 2 XP wins".

**Done when:**
- [ ] Playing gives XP, never shop points.
- [ ] A third Memory Match win in one day gives 0 XP and says so.

### Phase 8: Leaderboard

- `LeaderboardRepository.fetchWeek(weekKey)` reads `weeklyVisits/{weekKey}` only, sorts per §3.6, and returns the top 20 plus the signed-in customer's place.
- **Screen:** title "This week", a "Resets in 3 days" line, a top-3 podium, then rows; your own row pinned. Remove the all-time tab (M3).
- **Claim:** on Home and Play load, check the last 4 week keys for a top-3 place without a `leaderboard_{weekKey}` XP event. If one is found, show the "You came #2 last week" card → claim → award XP and unlock `juara_minggu`.

**Done when:**
- [ ] The board matches the seed data's visit counts.
- [ ] A last-week winner can claim once, and the reward can't be claimed for a week older than 4 weeks.

### Phase 9: Customer screens

Bring every customer screen fully onto BRAND_DESIGN and the new words. The `hu_*` screens already exist, so this phase is about content and copy:

| Screen | Changes |
|---|---|
| Home | Rank card (replaces level score), top-3 claim card when one applies, shop cards with new cups |
| Shops / Shop page | "10 points a visit" and "15 for you", cups |
| Scan | Result shows points and XP lines; first-scan view updated |
| Rewards | Unchanged flow; copy pass |
| Play | Rank card, then tiles: Missions, Spin the Wheel, Memory Match, Leaderboard. No badges here |
| Missions, Leaderboard, Spin, Memory | Per phases 5, 7 and 8 |
| Me | Profile, rank card, Badges grid, language, log out |
| Welcome / first-run | Explains points vs XP in at most 3 short cards |

The "How points work" link becomes a "How it works" sheet: points (per shop, spend on rewards), XP (rank up), rank bonus. Three short paragraphs.

### Phase 10: Shop-owner screens

Rebuild on BRAND_DESIGN; the old `sapot_*` look is removed.

| Screen (new tab) | Content |
|---|---|
| Home | Shop status (open/closed switch), this week's visits, points given, vouchers used. 3 stat tiles, then recent visits |
| Rewards (`RewardRulesActivity`, `AddEditRuleActivity`) | Reward rows with active switch; editor with name, cost in points, optional photo |
| Show QR (centre button) | Full-screen rotating QR, shop colour frame, "Ask customers to scan this" |
| Redeem (`ScanToRedeemActivity`) | Camera, then result sheet: voucher name, customer, "Mark as used" |
| Shop (`ShopProfileActivity` + `ShopSettingsActivity`, merged) | Details, hours, category, **points per visit**, photos, log out |

### Phase 11: Sign-in screens

| Screen | Content |
|---|---|
| Splash | Logo on white, no text |
| Login (one screen, M1) | Email, password, "Log in", "Forgot password?", "New here? Create an account" |
| Sign up: choose role | Two big tiles: "I'm a customer" / "I own a shop" |
| Sign up: customer | Name, email, password, "Create account" |
| Sign up: shop owner | Name, email, password, shop name, category, address, SSM number, "Create shop" |

Use the BRAND_DESIGN input styles with inline errors. No subheadings that repeat the title.

### Phase 12: Check and ship

- **Unit tests:** ranks, rounding, week keys, mission completion, badge rules, leaderboard sort.
- **Two-phone test** (one customer, one shop owner), real device:
  - [ ] Owner sets 20 points per visit. A rank-1 customer scans and gets 20 points + 15 XP.
  - [ ] The customer crosses 400 XP: the rank-up sheet appears, and the next scan gives 25 points.
  - [ ] All 3 weekly missions completed and claimed; a weekly reset checked by changing the phone's date.
  - [ ] Every badge unlocked, opened and claimed once.
  - [ ] Games give XP and respect the daily limits.
  - [ ] A last-week top-3 customer claims their reward once.
  - [ ] A customer claims a reward, the owner redeems it, and `belanja_diri` unlocks.
  - [ ] Every screen checked in EN and MS at the largest font size: nothing clips, no `sapot_*` look left.
- **Copy audit:** walk every screen against BRAND_DESIGN §2. No subheading that repeats its heading, no jargon, glossary words only.
- Update `docs/DATABASE.md` and `docs/firebase-rules.json` to match what shipped.

---

## 6. Working rules for this refactor

1. **One phase per branch and PR**, named `refactor/phase-N-short-name`.
2. **Tunable numbers live in `AppConfig.kt`.** None in screens.
3. **Every reward write is one atomic multi-path update.** Never two writes that can half-fail.
4. **Every check runs against the database,** never the phone's clock or local storage (game limits, cooldowns, claims).
5. **Every new string goes in EN and MS** in the same commit.
6. **Delete the replaced code in the same PR.** No commented-out code, no `Old`/`Legacy`/`V2` names.
7. **Run on the real phone** before calling a phase done.
