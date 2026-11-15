# Set up SapotLokal on your own Firebase project

This guide connects the app to **your** Firebase project and moves any data you already have into the new
database shape. It is written so you can follow it yourself, or hand it to an AI coding assistant (Claude Code,
Cursor, Copilot…) and say:

> Read `docs/SETUP.md` and help me set up SapotLokal on my Firebase project `<your-project-id>`.
> I already have data in it from the old version of the app.

There is a short section for the assistant at the end: [Notes for AI assistants](#notes-for-ai-assistants).

**Time:** about 30–45 minutes. **You need:** Owner access to the Firebase project, Android Studio, Node.js 18+, and a
phone or emulator.

---

## What changed in the database

If your project was used by the **old** app (before the refactor), its data has a different shape. In short:

| Old | New |
|---|---|
| One shared number, `users/{uid}/lifetimePoints`, also raised by games and missions | **Points** belong to one shop and are spent on rewards (`/points`). **XP** belongs to the customer and sets their rank (`users/{uid}/xp`, `/xpEvents`) |
| `currentTier` (Freshie → Sapot GOAT) | `rank` 1–5 (Jiran Baru → Legenda Sapot), from XP |
| `unlockedBadges` booleans | `/badges/{uid}/{badgeId}` with `unlockedAt` and `claimedAt` |
| `/missions`, `/userMissions`, `/userMissionTracking`, `/shopVisits` | `/missionProgress`, `/userStats`, `/weeklyVisits` |
| `/points` rows with no shop (game and mission points) | Removed: games and missions give XP |
| `games/*/gamePointsRewarded` | `gameXpRewarded` |
| Shops gave a fixed 10 points | Each shop picks `pointsPerVisit` (5–50) |

The full new shape is in [DATABASE.md](DATABASE.md). The migration script in step 5 does the move for you and keeps
every real account, shop, reward, voucher and **shop-points balance**.

> **The old app stops working after this.** The new security rules (step 6) don't accept the old app's writes. Install
> the new app on every phone that uses SapotLokal (customers and shop owners) at the same time.

---

## 0. Get the new code

1. **Merge the pull request** on GitHub (the repo's *Pull requests* tab → the rebuild PR → **Merge pull request** →
   **Confirm merge**). This also deletes the old web prototype files, including `package.json` and `pnpm-lock.yaml`:
   pnpm is no longer used. The only Node tools left are in `tools/seed` and `functions`, and they use plain `npm`.
2. **Update your copy on your computer.** In a terminal, in your existing `SapotLokal` folder:

   ```powershell
   git status            # should say "nothing to commit"; if not, commit or stash your changes first
   git checkout main
   git pull origin main
   ```

   Don't have the repo on this computer yet? `git clone https://github.com/maizatulsofea/SapotLokal.git` instead.
3. **Tidy leftovers from the old version** (they're git-ignored, so `git pull` leaves them behind). Delete these
   folders in the repo root if they exist: `node_modules`, `dist`, `.vite`. Your `app/google-services.json` stays:
   it's still needed.
4. In Android Studio: **File → Sync Project with Gradle Files**.

## 1. Check your Firebase project

In the [Firebase Console](https://console.firebase.google.com), open your project and check:

1. **Authentication → Sign-in method:** *Email/Password* is enabled.
2. **Realtime Database:** a database exists. Note its address from the top of the **Data** tab, for example
   `https://my-project-default-rtdb.asia-southeast1.firebasedatabase.app`. SapotLokal expects the
   **asia-southeast1 (Singapore)** location; another location works too, you just use its address below.
3. **Project settings → General → Your apps:** an **Android app** with package name `com.sapotlokal`.
   If there isn't one, click *Add app → Android*, enter `com.sapotlokal`, and skip the SHA step.
4. Download **`google-services.json`** from that Android app and put it at `app/google-services.json`.
   It is git-ignored on purpose: never commit it.

## 2. Point the code at your project

Your project id is in `google-services.json` (`"project_id"`). Change these three places:

| File | What to change |
|---|---|
| `app/src/main/kotlin/com/sapotlokal/AppConfig.kt` | `DATABASE_URL` → your database address from step 1 |
| `.firebaserc` | `"default"` → your project id |
| `functions/index.js` | `instance:` → your database name (the part before `.asia-southeast1…`, for example `my-project-default-rtdb`). Only matters if you use the push notifications in step 9 |

The scripts in `tools/seed` work out the database from the service-account key (step 4), or from a `DATABASE_URL`
environment variable if your database isn't in asia-southeast1.

## 3. Install the tools

```powershell
cd <path-to>\SapotLokal\tools\seed
npm install
npx firebase-tools login
```

`firebase-tools` runs through `npx`, so nothing else needs installing.

## 4. Back up your data and get a service-account key

**Back up first.** Realtime Database → **Data** tab → ⋮ menu → **Export JSON**. Keep the file somewhere safe. You can also
do it from the command line:

```powershell
npx firebase-tools database:get / --project <your-project-id> > backup-before-migration.json
```

**Service-account key** (lets the scripts read and write your database with admin access; it's a secret):
Project settings → **Service accounts** → **Generate new private key**. Save it as
`tools\seed\serviceAccountKey.json`. It is git-ignored; never commit or share it.

## 5. Move existing data to the new shape

Skip this step on a brand-new project with no data.

```powershell
cd <path-to>\SapotLokal\tools\seed
$env:GOOGLE_APPLICATION_CREDENTIALS = "<path-to>\SapotLokal\tools\seed\serviceAccountKey.json"
# Only if your database is NOT in asia-southeast1:
# $env:DATABASE_URL = "https://<your-database-address>"

npm run migrate
```

That is a **dry run**: it prints what it would change and writes nothing. Check the project and database lines at the
top are yours, and that the numbers look right. Then:

```powershell
npm run migrate -- --yes
```

What it does, in detail, is written at the top of [`tools/seed/migrate.js`](../tools/seed/migrate.js). Highlights:

- **XP:** each customer starts with 15 XP per past shop visit (the same as a visit is worth now), and the matching rank.
  The old `lifetimePoints` number is not carried over: it mixed shop points with game and mission points.
- **Shop points stay as they were.** Every `/points` row tied to a shop is kept, so balances and vouchers don't move.
- **Badges** that mean the same thing now are carried over as *unlocked*; customers tap to claim their XP. Old
  badges that no longer exist (Tapah Ranger, Raja Sapot) are dropped.
- **This week's leaderboard** is filled from this week's visits.

Running it a second time is safe: it only changes what is still in the old shape.

## 6. Deploy the security rules

```powershell
cd <path-to>\SapotLokal
npx firebase-tools deploy --only database --project <your-project-id>
```

This publishes [`docs/firebase-rules.json`](firebase-rules.json). From now on the app (not the old one) can read and
write the database.

## 7. Optional: add test data

`tools/seed` can add 10 test shops, 15 rewards and 10 test customers, all with ids starting `seed_`, and remove them all
again later. See [tools/seed/README.md](../tools/seed/README.md). Don't run it on a project real customers use unless
you plan to clean it up.

## 8. Build and install the app

In Android Studio: **File → Open** the `SapotLokal` folder, wait for Gradle sync, pick your phone, press **Run**. Or:

```powershell
cd <path-to>\SapotLokal
.\gradlew.bat :app:installDebug
```

Gradle needs a JDK 17+; Android Studio's own one works (`C:\Program Files\Android\Android Studio\jbr`).

## 9. Optional: "shop is open" push notifications

`functions/` holds one Cloud Function that tells customers when a shop they visit opens. Cloud Functions need the
**Blaze** (pay-as-you-go) plan. The app works fine without it.

```powershell
cd <path-to>\SapotLokal\functions
npm install
cd ..
npx firebase-tools deploy --only functions --project <your-project-id>
```

## 10. Check it works

- [ ] Sign in as a **shop owner**. Home shows your shop; Shop tab → pick points per visit → Save.
- [ ] Shop owner → **Show QR**: a QR with a 6-character code under it.
- [ ] Sign in as a **customer** on another phone. Scan the QR (or type the code on the Scan tab): you get points at that
      shop and 15 XP.
- [ ] Customer → Rewards: claim a reward. Shop owner → Redeem: type the voucher code → Mark as used.
- [ ] Customer → Play: spin the wheel once (it then says "Come back tomorrow").
- [ ] A customer who existed before the migration still has the same points at each shop.

## When you're done

Delete `tools\seed\serviceAccountKey.json`, and in the Firebase Console (Project settings → Service accounts) delete
that key too. Make a new one whenever you need the scripts again.

## Something went wrong?

| Problem | Fix |
|---|---|
| App shows nothing / "Couldn't load" | `AppConfig.DATABASE_URL` doesn't match your database address, or the rules (step 6) aren't deployed. |
| "Permission denied" in the app | Deploy the rules (step 6). Then sign out and in again. |
| Script says the project or database is wrong | Check `serviceAccountKey.json` is from your project; set `DATABASE_URL` if your database isn't in asia-southeast1. |
| Want to undo the migration | Realtime Database → Data → ⋮ → **Import JSON** with your backup from step 4. |
| Build fails: "google-services.json is missing" | Step 1.4. |

---

## Notes for AI assistants

You are helping the project owner run this guide. Please:

1. **Read first:** this file, [DATABASE.md](DATABASE.md), [`CLAUDE.md`](../CLAUDE.md) (project rules) and the header
   comment of [`tools/seed/migrate.js`](../tools/seed/migrate.js).
2. **Never commit or print secrets:** `app/google-services.json`, `tools/seed/serviceAccountKey.json`, passwords.
3. **Writing to the database is the owner's decision.** Make sure step 4's backup exists, run `npm run migrate` (dry
   run), show the owner the output, and run `npm run migrate -- --yes` only after they say yes. The same for
   `firebase deploy` and `npm run seed -- --yes`.
4. **Check the target first:** the scripts print `Project:` and `Database:` before doing anything. Stop if they aren't
   the owner's project.
5. **Order matters:** migrate (step 5) before deploying rules (step 6), and tell the owner every phone needs the new app.
6. If the owner's data has something the migration doesn't expect (for example extra nodes), stop and ask; don't delete
   data the script doesn't list.
