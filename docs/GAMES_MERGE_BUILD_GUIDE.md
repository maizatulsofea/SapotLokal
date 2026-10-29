# SapotLokal — Games Merge Build Guide

> **Audience:** Antigravity (AI coding agent) working inside the **main SapotLokal repo** (`com.sapotlokal`).
> **Goal:** Merge the standalone `SapotLokalGames` project (Spin the Wheel + Memory Match) into the main app so that both games are reachable from Customer Home, write real points to Firebase, and respect every rule the main app already follows.
> **Inputs:** `MAIN_APP_REPORT.md` (state of the main app, 2026-10-21) and `GAME_SPEC.md` (state of the games project, 2026-10-11).
> **Source of the game code:** `c:\Users\myzat\AndroidStudioProjects\SapotLokalGames` — treat as **read-only**. If it is not reachable, ask the developer for the path before Phase 4. `GAME_SPEC.md` does not contain the full source of `WheelView`, `buildGrid()`, `flipTo()`, `showState()` or `updateInfo()`; those must be read from the real files (or rebuilt from the spec descriptions in §4, §6 and §8 if the files are truly unavailable).

---

## 0. How to work through this guide

1. Work **in phase order**. Do not start a phase until the previous phase's **Gate** passes.
2. Create a branch first: `git checkout -b feature/games-merge`. **Commit at the end of every phase** (`games-merge: phase N – <title>`).
3. After every phase run `./gradlew assembleDebug`. A red build is a failed gate.
4. Do **not** touch anything listed in **Appendix A (Do-not list)**.
5. When something in the real code differs from what this guide assumes (for example the exact name of a private field in `PointRepository`), **follow the real code's pattern**, and note the difference in your final report. Do not guess silently.
6. Finish with the report in **Appendix C**.

---

## 1. Analysis — what the two codebases are

| | Main app (`SapotLokal`) | Games project (`SapotLokalGames`) |
|---|---|---|
| Package / applicationId | `com.sapotlokal` | `com.sapotlokal.app` |
| Source folder | `src/main/kotlin` | `src/main/java` |
| AGP / compileSdk / minSdk | 9.4.0 / 37 / 26 | 9.4.1 / 37 / 26 |
| JVM target | 21 | 11 |
| Theme parent | `Theme.MaterialComponents.Light.NoActionBar` | `Theme.Material3.DayNight.NoActionBar` |
| Dark mode | none (no `values-night`) | `values-night/themes.xml` (empty overrides) |
| View access | ViewBinding (`viewBinding = true`) | `findViewById` |
| Auth | Firebase Auth, uid via `currentUser?.uid` | none |
| Points storage | Firebase `/points` ledger, regional DB URL via `AppConfig.DATABASE_URL` | `SharedPreferences("games_sandbox")` via `FakePointsRepository` |
| Daily limits | must be checked against live DB (Rule 43) | local prefs date compare |
| Config | `AppConfig.kt` is the single source of truth (Rule 8) | `GameConfig.kt` holds everything |
| Entry point | `SplashActivity` | `MainActivity` hub (launcher) |
| Dependencies needed by games | all already present | core-ktx, appcompat, activity-ktx, constraintlayout, material |

### What this means

**Merges cleanly (no work beyond repackaging):** game logic (`spin()`, `onCardTapped()`, `onWin()` state machine), `WheelView` canvas drawing, card flip animation, Memory Match card model, emoji list, animation timings.

**Must be replaced, not ported:** `FakePointsRepository`, the `PointsRepository` interface (the main app already has `PointRepository` — **no "s"**), `SharedPreferences` persistence, the `Reset (testing only)` button and `resetAll()`, the games `MainActivity` hub (becomes `GamesMenuActivity`), the games `AndroidManifest.xml`, all three `res/values*` files, launcher icons, `values-night`.

**Gradle:** **no changes required.** Every library the games use is already in `app/build.gradle.kts`. Do not copy the games' `build.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts`, or Gradle wrapper. Keep AGP 9.4.0, JVM 21.

### Real conflicts and risks found

| # | Conflict | Consequence if ignored | Resolution (this guide) |
|---|---|---|---|
| C1 | Package `com.sapotlokal.app` vs `com.sapotlokal` | Wrong `R` import, duplicate-looking classes, manifest name errors | Repackage everything to `com.sapotlokal.*` (§3) |
| C2 | Material3 vs MaterialComponents theme | Any `Widget.Material3.*` style or `?attr/` M3 token **crashes at inflate time** under the main theme | Use only `Widget.MaterialComponents.*` / existing `Widget.SapotLokal.Button` (Phase 4) |
| C3 | Games have a second launcher `MainActivity` | Two launcher icons / wrong start screen | Never copy the games manifest (Phase 4) |
| C4 | `GameConfig` constants overlap `AppConfig` (`MEMORY_WIN_POINTS` = `MEMORY_MATCH_REWARD_POINTS` = 20; spin 5–50 = `SPIN_WHEEL_MIN/MAX`; `MEMORY_DAILY_CAP_POINTS` = 60 is new) | Two sources of truth, violates Rule 8 | Point/economy numbers live in `AppConfig`; `GameConfig` keeps presentation-only values (Phase 1) |
| C5 | Games use `SOURCE_SPIN` / `SOURCE_MEMORY` strings | Duplicates `PointSource.SPIN_WHEEL` / `MEMORY_MATCH` | Use `PointSource` (already in `Models.kt`) |
| C6 | `POINT` rows require a `vendorID`, game points have no shop | Phantom shop `""` appears in per-shop balances | Game rows use `vendorID = ""`; per-shop maths skips blank vendors (Phase 2) |
| C7 | `Game` data class has **no date field**; daily limits need one | Cannot check "once per day" against the DB | Add `gameDay` + `gamePlayedAt` to `Game` (Phase 2, logged in `erd-changes.md`) |
| C8 | Games write points synchronously to prefs; Firebase is async | A failed write after the animation starts = free spin | Write first, animate **only on success** (Phase 4) |
| C9 | Game row and point row written separately could diverge | Rule 9 broken (game with no ledger row) | One atomic multi-path `updateChildren` (Phase 2) |
| C10 | `/points` is append-only (`.validate`) | Update attempts fail | Always `push()` new rows (already the pattern) |
| C11 | Current `/games` rule is `.write` with no `!data.exists()` and no `.validate` | Game rows editable/forgeable | Tighten rules (Phase 2) |
| C12 | Rotation mid-spin loses `isSpinning`/result | Confusing UI | Lock the three game Activities to portrait (Phase 4) |
| C13 | `Handler.postDelayed` callbacks survive Activity destroy | Late DB write after leaving, leaks | `removeCallbacksAndMessages(null)` in `onDestroy` + `isFinishing` guards (Phase 4) |
| C14 | No `Application` class; every DB ref must pass the regional URL | Calling `FirebaseDatabase.getInstance()` with no args silently breaks reads | Always use `AppConfig.DATABASE_URL`; grep gate (Phase 6) |

---

## 2. Decisions locked in by this guide

These resolve open decisions from the main build guide and the games spec. **The developer can edit any of them before running the guide.** If an item is changed, update the dependent phase.

| ID | Decision | Default used here | Why |
|---|---|---|---|
| D1 | Do game points belong to a shop? | **No.** `vendorID = ""`. Game points raise **lifetime points / level** but are **not spendable** on any shop's vouchers. | Matches "level is from earned points" (Open Decision #12) and keeps per-shop voucher balances honest. **Developer must confirm** — the alternative (a separate spendable "bonus balance") is a bigger data-model change. |
| D2 | Where do tunable numbers live? | Points/economy/rules → `AppConfig`. Pure presentation (animation ms, emoji set, grid columns, flip timing) → `GameConfig` in `ui/games`. | Rule 8 for anything that changes the economy; keeps UI constants near the UI. |
| D3 | Reset timezone | Keep `Asia/Kuala_Lumpur`, stored as `AppConfig.GAMES_RESET_TIME_ZONE`. | App is Malaysia-focused; answers games spec Q2. |
| D4 | Daily-limit source of truth | Live Firebase `/games` rows (never local storage). | Rule 43. |
| D5 | Memory Match rows | One `/games` row per **win** (even if 0 points were awarded due to cap); a `/points` row only if `award > 0`. | Keeps attempt counts accurate; no zero-value ledger rows. |
| D6 | Remote-configurable segments/emoji (spec Q1), more pairs (Q4), sound/haptics (Q5) | **Not in this merge.** | Out of scope; revisit in Phase 7 polish. |
| D7 | Language of new strings | English, in `strings.xml`, prefix `games_`. | Matches existing single `strings.xml`. |
| D8 | Orientation | Game Activities locked to portrait. | See C12. |

---

## 3. Merge map (file by file)

Destination root: `app/src/main/kotlin/com/sapotlokal/` (Kotlin) and `app/src/main/res/` (resources).

| Games file | Action | Destination |
|---|---|---|
| `config/GameConfig.kt` | **Split** | Economy numbers → `AppConfig.kt`. Presentation numbers → `ui/games/GameConfig.kt` (new) |
| `data/PointsRepository.kt` | **Do not port** | Replaced by existing `PointRepository` + new `GamesRepository` |
| `data/FakePointsRepository.kt` | **Do not port** | Replaced by Firebase-backed `data/repository/GamesRepository.kt` (new) |
| `MainActivity.kt` | **Rewrite** | `ui/games/GamesMenuActivity.kt` (new, no Reset button, no launcher) |
| `spin/SpinWheelActivity.kt` | **Port + rewrite persistence** | `ui/games/SpinWheelActivity.kt` |
| `spin/WheelView.kt` | **Port, repackage, colours → resources** | `ui/games/WheelView.kt` |
| `memory/MemoryMatchActivity.kt` | **Port + rewrite persistence** | `ui/games/MemoryMatchActivity.kt` |
| `res/layout/activity_main.xml` | **Rewrite** | `res/layout/activity_games_menu.xml` |
| `res/layout/activity_spin_wheel.xml` | **Port, restyle** | `res/layout/activity_spin_wheel.xml` |
| `res/layout/activity_memory_match.xml` | **Port, restyle** | `res/layout/activity_memory_match.xml` |
| `res/values/colors.xml` (black/white) | **Do not copy** | Add `game_*` colours to main `colors.xml` instead |
| `res/values/strings.xml` | **Do not copy** | Add `games_*` strings to main `strings.xml` |
| `res/values/themes.xml`, `values-night/themes.xml` | **Do not copy** | Main theme stays |
| `res/mipmap-*`, `drawable/ic_launcher_*` | **Do not copy** | Main launcher icons stay |
| `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | **Do not copy** | — |
| `AndroidManifest.xml` | **Do not copy** | Add 3 `<activity>` lines to main manifest |
| `keepRules/rules.keep` | **Do not copy** | — |
| `build.gradle.kts`, `libs.versions.toml`, `settings.gradle.kts`, wrapper | **Do not copy** | — |

---

## Phase 0 — Preflight

1. `git status` must be clean on `main`; then create `feature/games-merge`.
2. Run `./gradlew assembleDebug` — it must pass **before** you change anything. If it fails, stop and report.
3. Open `PointRepository.kt` and note (you will need these in Phase 2):
   - whether it is an `object` (the report shows it called statically, so it should be);
   - the name of its private database reference;
   - the exact query pattern used in `fetchAllPointsForUser()` / `listenToPointsForUser()` (you will mirror it for `/games`);
   - how `isNetworkAvailable()` is implemented in `ScanActivity` (private function vs shared helper).
4. Re-confirm the name-clash list from the report is still true. Each of these searches must return **nothing**: `SpinWheelActivity`, `MemoryMatchActivity`, `WheelView`, `GameConfig`, `GamesRepository`, `GamesMenuActivity`, `activity_spin_wheel`, `activity_memory_match`, `activity_games_menu`, `object GameType`, `btnGames`.
5. Confirm the games source folder is readable. If not, ask the developer.

**Gate 0:** clean baseline build, all clash searches empty, notes from step 3 recorded.

---

## Phase 1 — Config (Rule 8)

### 1.1 Edit `AppConfig.kt`

Replace the existing spin/memory constants with the block below. Keep `SPIN_WHEEL_DAILY_LIMIT` as it is. No class reads `SPIN_WHEEL_MIN_POINTS` / `MAX_POINTS` yet (confirmed in the report), so changing them from `const val` to derived getters is safe.

```kotlin
// ── Games ─────────────────────────────────────────────────────────────────

/** Point values on the Spin the Wheel segments, in wheel order. One is picked uniformly at random. */
val SPIN_WHEEL_SEGMENTS: List<Int> = listOf(5, 10, 15, 20, 25, 30, 40, 50)

/** Derived from the segments so they can never disagree with the wheel. */
val SPIN_WHEEL_MIN_POINTS: Int get() = SPIN_WHEEL_SEGMENTS.min()
val SPIN_WHEEL_MAX_POINTS: Int get() = SPIN_WHEEL_SEGMENTS.max()

/** Points for one Memory Match win (before the daily cap). */
const val MEMORY_MATCH_REWARD_POINTS: Int = 20

/** Max Memory Match points a customer can earn per calendar day (3 wins at 20). */
const val MEMORY_MATCH_DAILY_CAP_POINTS: Int = 60

/** Timezone used to decide when "a new day" starts for both games. */
const val GAMES_RESET_TIME_ZONE: String = "Asia/Kuala_Lumpur"
```

Update the KDoc at the top of `AppConfig` so the "OPEN DECISIONS" note says the spin range and memory reward are now confirmed by the game prototype (still flag them for the developer to confirm in Phase 6 sign-off).

### 1.2 Create `ui/games/GameConfig.kt` (presentation only)

```kotlin
package com.sapotlokal.ui.games

/**
 * Presentation-only constants for the games (animation timing, layout, symbols).
 * NEVER put points, limits, or anything that changes the economy here — that lives in AppConfig (Rule 8).
 */
object GameConfig {
    const val SPIN_DURATION_MS = 4000L
    const val SPIN_EXTRA_ROTATIONS = 5

    val MEMORY_EMOJIS = listOf("🍰", "☕", "🍜", "🥐", "🍦", "🧋")
    const val MEMORY_COLUMNS = 4
    const val MEMORY_MISMATCH_DELAY_MS = 800L
    const val MEMORY_MATCH_REVEAL_DELAY_MS = 250L
    const val MEMORY_FLIP_HALF_MS = 120L
}
```

> `SOURCE_SPIN`, `SOURCE_MEMORY`, `RESET_TIME_ZONE`, `SPIN_SEGMENTS`, `MEMORY_WIN_POINTS`, `MEMORY_DAILY_CAP_POINTS` are **intentionally not copied**.

**Gate 1:** build passes; no other file references removed constants.

---

## Phase 2 — Data layer (Rules 9, 10, 43)

### 2.1 `Models.kt`

Add two fields to `Game` (defaults keep it Firebase-compatible) and a `GameType` object. Search first to make sure `GameType` does not already exist.

```kotlin
data class Game(
    val gameID: String = "",
    val userID: String = "",
    val gameType: String = "",        // GameType.SPIN_WHEEL or GameType.MEMORY_MATCH
    val gameAttempt: Int = 0,
    val gamePointsRewarded: Int = 0,
    val gameDay: String = "",         // NEW: "yyyy-MM-dd" in AppConfig.GAMES_RESET_TIME_ZONE
    val gamePlayedAt: Long = 0L       // NEW: epoch ms, for audit/ordering
)

object GameType {
    const val SPIN_WHEEL   = "spin_wheel"
    const val MEMORY_MATCH = "memory_match"
}
```

### 2.2 `PointRepository.kt` — two small edits

**(a)** Add a helper that builds a game ledger row. This keeps *all* construction of `Point` rows inside `PointRepository` so the ledger pattern stays in one place.

```kotlin
/** Game points belong to no shop. See docs/erd-changes.md Change 5. */
const val NO_VENDOR_ID = ""

/** Builds (but does not write) a ledger row for game points. Returns null if no push key could be made. */
fun newGamePointEntry(userID: String, source: String, amount: Int): Point? {
    val key = db.child("points").push().key ?: return null   // use the file's real db ref name
    return Point(
        pointID = key,
        userID = userID,
        vendorID = NO_VENDOR_ID,
        pointEarnedAmount = amount,
        pointRedeemedAmount = 0,
        pointSource = source
    )
}
```

**(b)** In the loop that builds `perShop` (≈ lines 84–93), keep `lifetimeEarned += ...` for **every** row, but only touch `perShop` when the row belongs to a shop:

```kotlin
lifetimeEarned += p.pointEarnedAmount
if (p.vendorID.isNotBlank()) {
    val current = perShop.getOrDefault(p.vendorID, 0)
    perShop[p.vendorID] = current + p.pointEarnedAmount - p.pointRedeemedAmount
}
```

Then **search every consumer of the per-shop map** (`FindShopsActivity`, anything calling `listenToPointsForUser` / `fetchAllPointsForUser`) and confirm none would break on a missing `""` key. `VoucherRepository.checkBalanceAndFetchRuleName()` already filters by `vendorID`, so it is unaffected — verify, don't assume.

### 2.3 Create `data/repository/GamesRepository.kt`

Follow the same style as `PointRepository` (an `object`, regional URL, callback style).

```kotlin
package com.sapotlokal.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.sapotlokal.AppConfig
import com.sapotlokal.data.model.Game
import com.sapotlokal.data.model.GameType
import com.sapotlokal.data.model.PointSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * GamesRepository — the ONLY place game plays are read or written.
 * Daily limits are computed from the live database, never from data stored on the phone (Rule 43).
 * Every play that earns points writes the /games row AND the /points row in ONE atomic update (Rule 9).
 */
object GamesRepository {

    private val db = FirebaseDatabase.getInstance(AppConfig.DATABASE_URL).reference

    fun todayKey(): String =
        LocalDate.now(ZoneId.of(AppConfig.GAMES_RESET_TIME_ZONE)).toString()

    data class GameStatus(
        val spinsToday: Int,
        val spinAttemptsTotal: Int,
        val memoryPointsToday: Int,
        val memoryAttemptsTotal: Int
    ) {
        val canSpinToday: Boolean get() = spinsToday < AppConfig.SPIN_WHEEL_DAILY_LIMIT
        val memoryRoomToday: Int
            get() = (AppConfig.MEMORY_MATCH_DAILY_CAP_POINTS - memoryPointsToday).coerceAtLeast(0)
    }

    /** One read gives everything the UI needs. onResult(null) = could not read (offline / permission). */
    fun fetchStatus(userID: String, onResult: (GameStatus?) -> Unit) {
        // Mirror the exact query pattern PointRepository.fetchAllPointsForUser() uses.
        db.child("games").orderByChild("userID").equalTo(userID).get()
            .addOnSuccessListener { snap ->
                val today = todayKey()
                var spinsToday = 0; var spinTotal = 0
                var memPointsToday = 0; var memTotal = 0
                for (child in snap.children) {
                    val g = child.getValue(Game::class.java) ?: continue
                    when (g.gameType) {
                        GameType.SPIN_WHEEL -> {
                            spinTotal++
                            if (g.gameDay == today) spinsToday++
                        }
                        GameType.MEMORY_MATCH -> {
                            memTotal++
                            if (g.gameDay == today) memPointsToday += g.gamePointsRewarded
                        }
                    }
                }
                onResult(GameStatus(spinsToday, spinTotal, memPointsToday, memTotal))
            }
            .addOnFailureListener { onResult(null) }
    }

    fun recordSpin(userID: String, pointsWon: Int, attemptNumber: Int, onResult: (Boolean) -> Unit) =
        recordPlay(userID, GameType.SPIN_WHEEL, PointSource.SPIN_WHEEL, pointsWon, attemptNumber, onResult)

    fun recordMemoryWin(userID: String, pointsAwarded: Int, attemptNumber: Int, onResult: (Boolean) -> Unit) =
        recordPlay(userID, GameType.MEMORY_MATCH, PointSource.MEMORY_MATCH, pointsAwarded, attemptNumber, onResult)

    private fun recordPlay(
        userID: String, gameType: String, pointSource: String,
        points: Int, attemptNumber: Int, onResult: (Boolean) -> Unit
    ) {
        val gameID = db.child("games").push().key ?: return onResult(false)
        val game = Game(
            gameID = gameID, userID = userID, gameType = gameType,
            gameAttempt = attemptNumber, gamePointsRewarded = points,
            gameDay = todayKey(), gamePlayedAt = System.currentTimeMillis()
        )
        val updates = hashMapOf<String, Any?>("games/$gameID" to game)
        if (points > 0) {
            val point = PointRepository.newGamePointEntry(userID, pointSource, points)
                ?: return onResult(false)
            updates["points/${point.pointID}"] = point
        }
        db.updateChildren(updates)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener { onResult(false) }
    }
}
```

> Use `push()` for every row. Never write to an existing key — `/points` is append-only (C10).

### 2.4 Tighten the `/games` Security Rules

In `docs/firebase-rules.json`, replace the `games` block with the version below. Note `.indexOn` and the query-based `.read` sit on the **parent** (`games`) because that is the path being queried.

```json
"games": {
  ".read": "auth != null && query.orderByChild == 'userID' && query.equalTo == auth.uid",
  ".indexOn": ["userID", "gameType"],
  "$gameID": {
    ".read": "auth != null && data.child('userID').val() === auth.uid",
    ".write": "auth != null && !data.exists() && newData.child('userID').val() === auth.uid",
    ".validate": "newData.hasChildren(['userID','gameType','gameAttempt','gamePointsRewarded','gameDay','gamePlayedAt']) && (newData.child('gameType').val() === 'spin_wheel' || newData.child('gameType').val() === 'memory_match') && newData.child('gamePointsRewarded').val() >= 0"
  }
}
```

**Important — verify before trusting:** the existing rules put `.indexOn` and `.read` inside `$pointID`, yet the report says the live points listener works, so the *deployed* rules may differ from the file. Open the deployed rules in the Firebase console, compare, and make `/games` follow whatever pattern actually works for `/points`. Test in the **Rules Playground**:
- authed as user A, read `/games` with `orderByChild=userID, equalTo=A` → allowed
- authed as user A, write a `/games/x` row with `userID = B` → denied
- authed as user A, overwrite an existing `/games/x` row → denied

Rules cannot be deployed by the agent. **Tell the developer to paste the updated rules into the Firebase console manually** and wait for confirmation before Phase 6.

### 2.5 Update docs (Rule 10)

Append to `docs/erd-changes.md`:

```markdown
## Change 4 — `gameDay` and `gamePlayedAt` added to `GAME`

**Phase added:** Games merge · **Status:** ⏳ Proposed — developer to confirm

**Problem:** `GAME` had no date, so "one spin per day" and "60 Memory Match points per day"
could not be checked against the live database (build guide Rule 43).

**Change:** `gameDay: String` ("yyyy-MM-dd" in Asia/Kuala_Lumpur) and `gamePlayedAt: Long` (epoch ms).
Security Rules make `GAME` rows append-only.

## Change 5 — Game points are written with an empty `vendorID`

**Phase added:** Games merge · **Status:** ⏳ Proposed — developer to confirm (Decision D1)

**Problem:** `POINT.vendorID` is a shop FK; game points belong to no shop.

**Change:** Game `POINT` rows use `vendorID = ""`. They count toward lifetime points and level,
but are excluded from per-shop balances, so they cannot be spent on a shop's vouchers.
```

Update `docs/database-structure.md`: add the two `games` fields and a note on `vendorID = ""` for `spin_wheel` / `memory_match` point rows.

**Gate 2:** build passes; `GamesRepository` compiles; no `FirebaseDatabase.getInstance()` call without `AppConfig.DATABASE_URL`; docs updated; developer has deployed the new rules.

---

## Phase 3 — Resources

### 3.1 `res/values/colors.xml` — append (do **not** add generic `black`/`white`)

Read the exact hex values from the games source (`WheelView.kt`, `MemoryMatchActivity.showState`). Expected values from the spec:

```xml
<!-- Games -->
<color name="game_wheel_segment_1">#FF8A65</color>
<color name="game_wheel_segment_2">#FFD54F</color>
<color name="game_wheel_segment_3">#81C784</color>
<color name="game_wheel_segment_4">#64B5F6</color>
<color name="game_wheel_hub">#FFFFFF</color>
<color name="game_wheel_label">#000000</color>
<color name="game_card_back">#64B5F6</color>
<color name="game_card_back_text">#FFFFFF</color>
<color name="game_card_front">#FFFFFF</color>
<color name="game_card_front_stroke">#64B5F6</color>
<color name="game_card_front_text">#000000</color>
<color name="game_card_matched">#C8E6C9</color>
```

> The wheel's blue/yellow/orange are close to the app's brand colours. That is fine for now — palette alignment with Figma is a **Phase 7** task. Do not "fix" it during the merge.

### 3.2 `res/values/strings.xml` — append

Move every user-facing string out of Kotlin. Suggested set (add any other literals you find in the games source):

```xml
<!-- Games -->
<string name="games_home_button">Games</string>
<string name="games_menu_title">Games</string>
<string name="games_spin_title">Spin the Wheel</string>
<string name="games_memory_title">Memory Match</string>
<string name="games_spin_button">Spin</string>
<string name="games_spin_checking">Checking…</string>
<string name="games_spin_come_back">Come back tomorrow</string>
<string name="games_spin_result">You won %1$d points!</string>
<string name="games_spin_available">Spin available today</string>
<string name="games_spin_used">Spun today</string>
<string name="games_memory_moves">Moves: %1$d</string>
<string name="games_memory_info">Memory points today: %1$d / %2$d</string>
<string name="games_memory_saving">Saving your points…</string>
<string name="games_memory_win_full">You won in %1$d moves! +%2$d points!</string>
<string name="games_memory_win_partial">You won in %1$d moves! +%2$d points (daily limit reached)</string>
<string name="games_memory_win_none">You won in %1$d moves! (daily points limit reached)</string>
<string name="games_memory_save_failed">You won! We couldn\'t save your points. Tap here to try again.</string>
<string name="games_new_game">New Game</string>
<string name="games_back">Back</string>
<string name="games_error_load">Couldn\'t load your game status. Please try again.</string>
<string name="games_error_save">Couldn\'t save your result. Please try again.</string>
<string name="games_error_not_logged_in">Not logged in. Please restart the app.</string>
```

Reuse the existing `error_no_internet` string — do not duplicate it.

**Gate 3:** build passes; resources compile.

---

## Phase 4 — Port the screens

### 4.1 Universal porting rules (apply to every ported file)

- `package com.sapotlokal.ui.games`; replace any `com.sapotlokal.app.*` import; `R` is `com.sapotlokal.R`.
- Files go under `src/main/kotlin/…`, never `src/main/java`.
- Replace `findViewById` with **ViewBinding** (`ActivitySpinWheelBinding`, `ActivityMemoryMatchBinding`, `ActivityGamesMenuBinding`).
- Remove every reference to `FakePointsRepository`, `PointsRepository`, `SharedPreferences`, `resetAll`, `GameConfig.SOURCE_*`.
- Colours via `ContextCompat.getColor(context, R.color.game_*)`; strings via `getString(R.string.games_*)`. No hex literals or user-facing text in Kotlin.
- **Styles (C2):** only `Widget.MaterialComponents.*` or `Widget.SapotLokal.Button`. Outlined buttons use `@style/Widget.MaterialComponents.Button.OutlinedButton` (same as My Vouchers). Replace any `Widget.Material3.*` or M3-only `?attr/` token you find in the games layouts.
- **Layout shell:** match Customer Home — `CoordinatorLayout`/`AppBarLayout` + `MaterialToolbar` (`colorPrimary`, `colorOnPrimary` title, back arrow as navigation icon), content in a `NestedScrollView` on `@color/colorBackground`. Drop the games' `fitsSystemWindows` root and the standalone "Back" `MaterialButton` where the toolbar back arrow replaces it (keep the IDs `backButton` if you keep the buttons).
- Keep these IDs (confirmed free in the main app): `wheel`, `resultText`, `spinNowButton`, `backButton`, `infoText`, `movesText`, `cardGrid`, `statusText`, `newGameButton`.
- Every game Activity starts by resolving the user:
  ```kotlin
  val userID = FirebaseAuth.getInstance().currentUser?.uid ?: run {
      Toast.makeText(this, R.string.games_error_not_logged_in, Toast.LENGTH_LONG).show()
      finish(); return
  }
  ```
- Network check: reuse the same approach as `ScanActivity.isNetworkAvailable()`. If it is a private function there, extract it **once** to `util/NetworkUtils.kt` and point `ScanActivity`, `ScanToRedeemActivity` and the new games at it — keep that change minimal and behaviour-identical.
- In asynchronous callbacks, start with `if (isFinishing || isDestroyed) return`.

### 4.2 `WheelView.kt`

Port as-is: same `onDraw` geometry (spec §8.6), same `values` setter API. Changes: package; colours from `R.color.game_wheel_*` (cycle segment colours with `i % 4`); hub/label paints from resources. Do **not** alter the arc offsets (`-90f + i*sweep - sweep/2`) — `spin()` maths depends on them. Feed it `AppConfig.SPIN_WHEEL_SEGMENTS`.

### 4.3 `SpinWheelActivity.kt` — async flow

Keep the animation exactly as in spec §8.2 (`DecelerateInterpolator`, `GameConfig.SPIN_DURATION_MS`, `GameConfig.SPIN_EXTRA_ROTATIONS`, jitter ±35% of a segment, positive `delta`). Only the gating and commit change:

```kotlin
// On open (onCreate/onResume): button disabled + "Checking…" until status arrives
private fun refreshStatus() {
    setBusy(true, R.string.games_spin_checking)
    GamesRepository.fetchStatus(userID) { status ->
        if (isFinishing || isDestroyed) return@fetchStatus
        if (status == null) { setBusy(false); showMessage(R.string.games_error_load); return@fetchStatus }
        applyStatus(status)               // enabled "Spin" or disabled "Come back tomorrow"
    }
}

private fun onSpinTapped() {
    if (isSpinning || isBusy) return
    if (!isNetworkAvailable()) { showMessage(R.string.error_no_internet); return }
    setBusy(true, R.string.games_spin_checking)

    GamesRepository.fetchStatus(userID) { status ->
        if (isFinishing || isDestroyed) return@fetchStatus
        if (status == null) { setBusy(false); showMessage(R.string.games_error_load); return@fetchStatus }
        if (!status.canSpinToday) { applyStatus(status); return@fetchStatus }

        val segments = AppConfig.SPIN_WHEEL_SEGMENTS
        val winnerIndex = Random.nextInt(segments.size)
        val pointsWon = segments[winnerIndex]

        // COMMIT FIRST. Animate only if the write succeeded (prevents free re-spins, C8).
        GamesRepository.recordSpin(userID, pointsWon, status.spinAttemptsTotal + 1) { ok ->
            if (isFinishing || isDestroyed) return@recordSpin   // points are saved even if the user left
            if (!ok) { setBusy(false); showMessage(R.string.games_error_save); return@recordSpin }
            animateWheelTo(winnerIndex, pointsWon)
        }
    }
}
```

`animateWheelTo()` = the existing spin maths + `withEndAction { isSpinning = false; resultText.text = getString(R.string.games_spin_result, pointsWon); setBusy(false); showSpunToday() }`.

### 4.4 `MemoryMatchActivity.kt`

Keep `startGame()`, `onCardTapped()`, the `Card` class, `flipTo()`, `showState()`, `buildGrid()` and the card colours/dimensions (88dp height, 16dp radius, 4dp margin) exactly as in the source. Replace literals with `GameConfig.*` / `R.string.*` / `R.color.game_*`. Changes:

1. **`onDestroy`:** `handler.removeCallbacksAndMessages(null)`.
2. **Delayed callbacks** (`postDelayed` for match reveal and mismatch flip-back): begin with `if (isFinishing || isDestroyed) return@postDelayed`.
3. **Win condition** compares `matchedPairs == GameConfig.MEMORY_EMOJIS.size`.
4. **`onWin()`** becomes asynchronous and reads the cap from the database:

```kotlin
private var pendingSaveMoves = 0

private fun onWin() {
    if (gameOver) return            // guard: only ever award once per game
    gameOver = true
    pendingSaveMoves = moves
    saveWin()
}

private fun saveWin() {
    binding.newGameButton.isEnabled = false
    binding.statusText.text = getString(R.string.games_memory_saving)
    binding.statusText.setOnClickListener(null)

    GamesRepository.fetchStatus(userID) { status ->
        if (isFinishing || isDestroyed) return@fetchStatus
        if (status == null) return@fetchStatus showSaveFailed()

        val award = minOf(AppConfig.MEMORY_MATCH_REWARD_POINTS, status.memoryRoomToday)
        GamesRepository.recordMemoryWin(userID, award, status.memoryAttemptsTotal + 1) { ok ->
            if (isFinishing || isDestroyed) return@recordMemoryWin
            if (!ok) return@recordMemoryWin showSaveFailed()

            binding.statusText.text = when {
                award <= 0 -> getString(R.string.games_memory_win_none, pendingSaveMoves)
                award < AppConfig.MEMORY_MATCH_REWARD_POINTS ->
                    getString(R.string.games_memory_win_partial, pendingSaveMoves, award)
                else -> getString(R.string.games_memory_win_full, pendingSaveMoves, award)
            }
            binding.newGameButton.isEnabled = true
            refreshInfo()
        }
    }
}

private fun showSaveFailed() {
    binding.statusText.text = getString(R.string.games_memory_save_failed)
    binding.statusText.setOnClickListener { saveWin() }   // retry without replaying
    binding.newGameButton.isEnabled = true
}
```

5. **`refreshInfo()`** (replaces `updateInfo()`): on open and after each win, call `fetchStatus` and show `games_memory_info` (`memoryPointsToday` / `AppConfig.MEMORY_MATCH_DAILY_CAP_POINTS`). If the read fails, show nothing rather than a wrong number.
5. `startGame()` must also clear the `statusText` click listener.
6. If the network is down when a game **starts**, still let the player play but keep the info line empty; saving is attempted (and retryable) at the end.

### 4.5 `GamesMenuActivity.kt` + `activity_games_menu.xml`

A small hub reached from Customer Home. Contents: toolbar "Games" with back arrow; two `MaterialCardView`s (same 16dp corner / 2dp elevation as the Points Card) — **Spin the Wheel** (subtitle from `games_spin_available` / `games_spin_used`) and **Memory Match** (subtitle `games_memory_info`). No Reset button, no total-points text (Home already shows it live). Call `GamesRepository.fetchStatus` in `onResume` to refresh subtitles; if it returns null leave subtitles blank.

### 4.6 `AndroidManifest.xml`

Add next to the Phase 4 vendor activities. No intent filters, nothing else changed:

```xml
<!-- Games (merged from SapotLokalGames) -->
<activity android:name=".ui.games.GamesMenuActivity"   android:exported="false" android:screenOrientation="portrait" />
<activity android:name=".ui.games.SpinWheelActivity"   android:exported="false" android:screenOrientation="portrait" />
<activity android:name=".ui.games.MemoryMatchActivity" android:exported="false" android:screenOrientation="portrait" />
```

**Gate 4:** build passes; no hex literals or user-facing text in the new Kotlin; no `Material3` string in the new layouts; `grep` for the leftovers listed in §6.2 is clean.

---

## Phase 5 — Customer Home entry point

### 5.1 `activity_customer_home.xml`

After `btnMyVouchers`, add (this is the smallest, safest change recommended by the report):

```xml
<View android:layout_width="0dp" android:layout_height="16dp" />

<com.google.android.material.button.MaterialButton
    android:id="@+id/btnGames"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:text="@string/games_home_button"
    app:icon="@android:drawable/ic_media_play" />
```

### 5.2 `CustomerHomeActivity.kt`

Where the other click listeners are wired:

```kotlin
binding.btnGames.setOnClickListener {
    startActivity(Intent(this, GamesMenuActivity::class.java))
}
```

Add the import `com.sapotlokal.ui.games.GamesMenuActivity`. **Do not** touch the points listener: it is detached in `onStop` and re-attached in `onStart`, so lifetime points and the level chip refresh automatically when the customer returns from a game.

**Gate 5:** build passes; Home shows the Games button; tapping it opens the hub.

---

## Phase 6 — Verification

### 6.1 Manual test matrix (two test customer accounts, A and B, on a real device or emulator)

| # | Scenario | Expected |
|---|---|---|
| 1 | A opens Games → Spin | Button briefly "Checking…" then "Spin" |
| 2 | A spins | Wheel animates ~4 s; "You won N points!" with N ∈ {5,10,15,20,25,30,40,50}; the wheel's pointer ends on segment N |
| 3 | Firebase console after #2 | **One** new `/games` row (`spin_wheel`, `gameDay` = today KL, `gamePointsRewarded` = N) **and one** new `/points` row (`pointSource` `spin_wheel`, `vendorID` `""`, `pointEarnedAmount` = N) |
| 4 | Back to Customer Home | Lifetime points rose by N; level chip/progress bar updated live without a manual refresh |
| 5 | A reopens Spin, force-stops and relaunches the app | Still "Come back tomorrow" (proves DB-based, not local) |
| 6 | Log in as B on the same phone | B can spin (limit is per user) |
| 7 | A taps Spin then immediately presses Back mid-animation | Points already in console; reopening shows "Come back tomorrow" |
| 8 | Airplane mode → Spin | Error toast, **no animation, no rows written** |
| 9 | Memory Match: win 1st game | `+20 points`; info line shows 20 / 60; one `/games` + one `/points` row (`memory_match`) |
| 10 | Win 2nd and 3rd game | `+20` each; info line 60 / 60 |
| 11 | Win a 4th game | "…(daily points limit reached)"; **a `/games` row with 0 points, no `/points` row** |
| 12 | Cap test: temporarily set `MEMORY_MATCH_REWARD_POINTS = 25`, win 3 games | 25, 25, **10** ("daily limit reached" partial message). **Revert to 20 afterwards.** |
| 13 | Win a game, kill app, reopen Memory Match | Info line still reflects points earned today |
| 14 | Win with airplane mode on | "couldn't save… tap to try again"; reconnect, tap the message → saves once; no duplicate rows on a second tap after success |
| 15 | Leave Memory Match mid-mismatch-delay | No crash, no late DB write |
| 16 | Rotate the phone in every game screen | Stays portrait |
| 17 | Day rollover: set device date to tomorrow (and timezone KL) | Spin available again; Memory cap reset (restore the date afterwards) |
| 18 | Shop regression: scan a shop QR as A | Still +10; the voucher balance for that shop is **unchanged by game points**; `ScanToRedeem` redemption unaffected |
| 19 | Vendor login | No path to Games from `VendorHomeActivity` |
| 20 | Rules Playground (§2.4) | All three cases behave as listed |

### 6.2 Code gates (all must pass)

Run these searches across `app/src`; each must return **no matches**:

- `FakePointsRepository`, `PointsRepository` (with an "s"), `SharedPreferences`, `resetAll`, `games_sandbox`
- `com.sapotlokal.app`
- `GameConfig.SOURCE_`, `SPIN_SEGMENTS` (the games' name — the main name is `SPIN_WHEEL_SEGMENTS`), `MEMORY_DAILY_CAP_POINTS` (games' name), `RESET_TIME_ZONE` (without the `GAMES_` prefix)
- `FirebaseDatabase.getInstance()` with **no argument** (C14)
- `Widget.Material3`, `Theme.Material3`
- `R.color.black`, `R.color.white` in the new games files (replace with `game_*` colours)
- `findViewById` in the three game Activities
- Hard-coded `"You won"`, `"Moves:"`, `"Come back tomorrow"` etc. in Kotlin (must come from `strings.xml`)

Also: `./gradlew assembleDebug` and `./gradlew lint` pass; no new lint errors in the new files.

### 6.3 Docs sync (Rule 10)

- `docs/erd-changes.md` has Changes 4 and 5.
- `docs/database-structure.md` updated.
- `docs/firebase-rules.json` matches what the developer deployed.
- If `SAPOTLOKAL_BUILD_GUIDE.md` is in the repo, tick **Phase 6 → Spin the Wheel** and **Memory Match** as done. Leave **level-up moment**, **Challenges** and **FCM** as not started.

**Gate 6:** every row in 6.1 passes, every search in 6.2 is clean, docs match the code.

---

## Phase 7 — Wrap-up

1. Final `./gradlew assembleDebug` on `feature/games-merge`.
2. Final commit; **do not merge to `main`** — leave that for the developer.
3. Produce the report in Appendix C.

---

## Appendix A — Do-not list

- Do **not** change `applicationId`, `namespace`, AGP, Gradle, JDK, `compileSdk`, `minSdk`, `targetSdk`, or any dependency.
- Do **not** copy the games' manifest, themes, `values-night`, launcher icons, backup rules or build files.
- Do **not** add a second launcher activity or any intent filter.
- Do **not** enable `setPersistenceEnabled(true)` or add an `Application` class in this merge.
- Do **not** modify the games source project.
- Do **not** write to or update existing `/points` or `/games` rows — `push()` only.
- Do **not** store any game state (daily limits, totals, last-spin date) on the device.
- Do **not** rename `PointRepository`.
- Do **not** remove the Phase 3/4 debug helpers in `ScanActivity` / `ShowQrActivity` — that is Phase 7 (polish) work, separate from this merge.
- Do **not** add sound, haptics, extra emoji pairs, remote config, or a level-up animation (see D6).
- Do **not** "fix" unrelated issues spotted along the way (see Appendix B). List them in the report instead.

## Appendix B — Known limitations and follow-ups (not part of this merge)

| # | Item |
|---|---|
| L1 | **Points rows are client-writable.** A modified client could write any `pointEarnedAmount` or bypass the daily limits (the `/points` rule only checks `userID`). True protection needs Cloud Functions or server-side validation. This limitation already applies to shop-visit points. |
| L2 | The daily limits use the **device clock** (in the KL timezone). Changing the phone date can bypass them. Server-time correction (`.info/serverTimeOffset`) would reduce this but adds complexity; revisit with L1. |
| L3 | The rule's `gamePointsRewarded >= 0` has no upper bound, because the maximum is an `AppConfig` number (Rule 8). |
| L4 | `/challenges` write rule is open to any signed-in user (report §9 #7) — tighten before the Challenges screen. |
| L5 | `CustomerHomeActivity` swallows points-listener errors (report §9 #9). |
| L6 | No level-up moment yet (Phase 6 remaining). |
| L7 | `POST_NOTIFICATIONS` runtime request and `SapotFcmService` are still stubs. |
| L8 | Wheel and card colours are not yet aligned to Figma (Phase 7). |
| L9 | Spin and Memory Match results are not yet counted toward Challenges; add a hook when Challenges are built. |

## Appendix C — Final report template

Report back to the developer with:

1. **Branch and commits** — list of commit hashes per phase.
2. **Files** — created / modified / deliberately not copied (match the §3 merge map).
3. **Deviations** — anything that differed from this guide (e.g. real field names in `PointRepository`, shared network helper extraction, rule-pattern differences found in the console).
4. **Decisions applied** — D1–D8, and which the developer still needs to confirm (D1 especially).
5. **Test results** — pass/fail for each row of §6.1, and the output of the §6.2 searches.
6. **Manual actions needed** — Firebase rules deployment status.
7. **Issues noticed but not fixed** — anything outside scope.
