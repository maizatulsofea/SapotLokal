# SapotLokal database

Firebase Realtime Database (asia-southeast1; the address is `AppConfig.DATABASE_URL`). Security rules: [firebase-rules.json](firebase-rules.json). The plan behind this shape: [REFRACTOR.md §4](../REFRACTOR.md).

**Coming from the old app version?** [SETUP.md](SETUP.md) explains the differences, and `npm run migrate` in `tools/seed` moves existing data to this shape.

There are two currencies, and they never mix:

- **Points** belong to one shop. Customers earn them by visiting and spend them on that shop's rewards. They're stored in `/points`, one row per change.
- **XP** belongs to the customer. It's never spent and it sets the rank. It's stored as a total in `users/{uid}/xp`, plus one row per award in `/xpEvents`.

## Nodes

| Node | Key | What it holds | Who writes |
|---|---|---|---|
| `users` | uid | `userID, userName, userEmail, role, avatar, xp, rank, rankSeen, fcmToken`. Other signed-in users can read only `userName, avatar, rank` | the user |
| `vendors` | vendorID (= owner uid) | shop details, `isOpen`, `operatingHours`, **`pointsPerVisit`** (5, 10, 15, 20, 25, 30, 40 or 50) | the owner |
| `vendorPhotos` | vendorID | one small Base64 JPEG | the owner |
| `qrcodes` | push id | rotating shop QR values | the owner |
| `points` | push id | shop-points ledger: `userID, vendorID, pointEarnedAmount, pointRedeemedAmount, pointSource` (`shop_visit` or `redemption`), `timestamp`, and for visits `basePoints, rankBonus` | customer (visit, claim), owner (redemption) |
| `xpEvents/{uid}` | event key | XP ledger: `amount, source, timestamp` | the customer |
| `userStats/{uid}` | – | `totalVisits, shopsVisited/{vendorID}, weeksAllMissions, lastVisitAt`; badge tracking `days/{yyyy-MM-dd}/{visit|spin|memory}` (Combo) and `shopDays/{weekKey}/{vendorID}/{yyyy-MM-dd}` (Regular) | the customer |
| `weeklyVisits/{weekKey}/{uid}` | week, uid | `visits, lastVisitAt, userName, avatar, rank` (the leaderboard's only source) | the customer |
| `missionProgress/{uid}` | `{missionId}_{weekKey}` or `{missionId}` | `progress, claimedAt`; `w_explore_{week}/shops/{vendorID}` and `w_play_{week}/days/{yyyy-MM-dd}` marks written by scans and games | the customer |
| `badges/{uid}` | badge id | `unlockedAt, claimedAt` | the customer |
| `vouchers` | push id | shop rewards: `vendorID, voucherName, voucherDescription, pointsRequired, isActive` | the owner |
| `voucherUsers` | push id | claimed vouchers: code, status (`active`, `used`, `expired`), expiry, cost snapshot, `claimPointID` | customer (claim), owner (mark used) |
| `games` | push id | one row per game played: `userID, gameType, gameAttempt, gameXpRewarded, gameDay, gamePlayedAt` | the customer |
| `shopNotifications` | push id | "shop is open" announcements for the push Cloud Function | the owner |

## Keys and formats

- **Week key:** ISO week in Malaysia time, e.g. `2026-W46`. The week starts Monday 00:00 Asia/Kuala_Lumpur (`MissionDateUtils.getWeekYearKey`).
- **XP event keys** are deterministic, so the rules can let each one be written only once. The amount cap depends on the key's prefix:

| Key | Cap |
|---|---|
| `visit_{pointID}` | 15 |
| `game_{gameID}` | 15 |
| `wmission_{missionId}_{weekKey}` | 40 |
| `lmission_{missionId}` | 300 |
| `badge_{badgeId}` | 100 |
| `leaderboard_{weekKey}` | 150 |

- **Day key:** `yyyy-MM-dd` in Malaysia time; a new day starts at midnight Asia/Kuala_Lumpur.
- **Badge ids:** `langkah_kanan, regular, hantu_malam, combo, nasib_baik, otak_cerdas, belanja_diri, juara_minggu`. When each one unlocks is in `BadgeRules` (`data/model/Badge.kt`); `belanja_diri` unlocks when the customer opens the app after a shop marks one of their rewards used, and `juara_minggu` when they claim a top-3 leaderboard prize.

## What the rules enforce

- Your own `users` record is private; other signed-in users see only `userName`, `avatar` and `rank`.
- `xp` never goes down, and each write raises it by at most 300. `rank` is 1–5 and moves up at most one step per write.
- A shop-visit row needs a real shop and earns at most that shop's `pointsPerVisit × 3`.
- `weeklyVisits/{week}/{uid}/visits` only goes up by 1 per write, and only the owner can write it. `xpEvents`, `userStats`, `missionProgress`, `badges` and `games` are readable only by their owner.
- `qrcodes` can only be looked up by exact value, so nobody can list live shop codes.
- Every time the app writes (visit, game, XP event, mission or badge claim, `lastVisitAt`) must be within 5 minutes of the server's clock. The app reads server time through `util/ServerClock` (Firebase's `.info/serverTimeOffset`), so day and week keys, game limits, the 12-hour scan cooldown and QR expiry don't move when someone changes their phone's date.
- `missionProgress` and `badges` timestamps can be set once and never changed.
- Claiming a voucher must write the matching redemption row in the same update, at the reward's real cost.

**Known limit:** with no server, a determined customer with a modified app can still award themselves small amounts, and the rules can't check that a day or week key matches the date. Every award is capped and leaves a trace in `/xpEvents`. Moving awards into Cloud Functions (Blaze plan) would close this gap.

## Test data

`tools/seed` writes 10 shops (with mixed points per visit), 15 rewards, and 10 customers spread across all 5 ranks. It also writes visit XP events, weekly leaderboard rows for every week with visits, and the badges the data already earns (unclaimed). Every id starts with `seed_`, and `npm run cleanup -- --yes` removes all of it. See [tools/seed/README.md](../tools/seed/README.md).
