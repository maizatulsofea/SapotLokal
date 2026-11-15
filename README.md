# SapotLokal

An Android loyalty app for small local shops in Malaysia, in English and Bahasa Melayu.

Customers scan a shop's QR code at the counter. Each visit earns **that shop's points**, which they spend on the shop's
rewards, and **XP**, which never gets spent and moves them up five ranks. A higher rank means more points at every
shop. Shop owners set how many points a visit is worth, add rewards, show their QR and redeem vouchers.

## What's in the app

**Customers**
- **Home:** the reward you can claim now (or your next one), recently visited shops, and today's games and missions.
- **Shops:** every shop with its photo, your points there and your progress to the next reward. Filter by Visited,
  Open now or type.
- **Scan:** scan the shop's QR, or type the 6-character code under it.
- **Rewards:** rewards to claim, your vouchers (show the QR at the counter), and what you're saving up for.
- **Play:** your rank, weekly and lifetime missions, Spin the wheel, Memory match, and the weekly leaderboard (most
  visits; the top 3 win XP).
- **Profile:** name and avatar, 8 badges to earn and claim, language, account.

**Shop owners**
- **Home:** open/closed switch, this week's visits and points, recent visits.
- **Rewards:** add and edit rewards. **Show QR:** a code that changes every minute. **Redeem:** scan or type a
  customer's voucher. **Shop:** details, photo, points per visit.

## Built with

- Kotlin, XML views with ViewBinding, Material Components. minSdk 26, single `app` module, package `com.sapotlokal`.
- Firebase: Authentication (email/password), Realtime Database (asia-southeast1), Cloud Messaging, and one optional
  Cloud Function.

## Getting started

**New to this repo, or moving the app to your own Firebase project?** Follow **[docs/SETUP.md](docs/SETUP.md)**. It
covers connecting your project, moving data from the old app version to the new database shape, deploying the security
rules and building the app. It also has notes for an AI assistant helping you.

Already set up:

```powershell
.\gradlew.bat :app:assembleDebug        # build
.\gradlew.bat :app:testDebugUnitTest    # unit tests
.\gradlew.bat :app:installDebug         # install on the connected phone
```

`app/google-services.json` (from the Firebase Console) is needed to build. It's a local secret and git-ignored.

## Repository map

| Path | What |
|---|---|
| `app/` | The Android app. Tunable numbers (points, XP, ranks, limits) are all in `AppConfig.kt`. |
| `docs/SETUP.md` | Step-by-step setup on your own Firebase project, including migrating old data. |
| `docs/DATABASE.md` | Every database node, its fields, and what the security rules enforce. |
| `docs/firebase-rules.json` | The Realtime Database security rules (deploy with `npx firebase-tools deploy --only database`). |
| `tools/seed/` | Node scripts: `migrate` (old data → new shape), `seed` (test data) and `cleanup` (remove test data). |
| `functions/` | Optional Cloud Function for "shop is open" notifications (needs the Blaze plan). |
| `BRAND_DESIGN.md` | Colours, type, components and copy rules every screen follows. |
| `REFRACTOR.md` | The plan behind the current version: economy, data model and the phase-by-phase rebuild. |
| `CLAUDE.md` | Project rules for AI coding assistants (and a good summary for people too). |

## House rules

1. Numbers that might be tuned live in `AppConfig.kt`, never in a screen.
2. Every reward is one atomic multi-path write.
3. Limits, cooldowns and claims are checked against the database and server time, never the phone's clock.
4. Every user-facing string exists in `values/` (English) and `values-ms/` (Malay).
