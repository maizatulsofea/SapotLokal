// Moves an existing SapotLokal database from the old shape (before the 2026 refactor) to the
// current one in docs/DATABASE.md, keeping every real account, shop, reward, voucher and shop-points
// balance.
//
//   npm run migrate              dry run: prints what would change, writes nothing
//   npm run migrate -- --yes     makes the changes
//
// Back up first (docs/SETUP.md, step 4). Uses GOOGLE_APPLICATION_CREDENTIALS like seed.js, and
// DATABASE_URL if your database is not the default asia-southeast1 one (see shared.js).
//
// What changes:
//   users/{uid}       lifetimePoints, currentTier and unlockedBadges are removed. Customers get
//                     xp = 15 XP per past shop visit (VISIT_XP), the matching rank, and rankSeen = rank
//                     so nobody gets a surprise rank-up sheet.
//   xpEvents/{uid}    one visit_{pointID} event (15 XP) per past shop visit, so XP has its paper trail.
//   userStats/{uid}   totalVisits, shopsVisited, lastVisitAt, weeksAllMissions = 0.
//   weeklyVisits      this week's visits, so the leaderboard isn't empty on day one.
//   badges/{uid}      badges that mean the same thing now are carried over as unlocked (the customer
//                     claims their XP in the app): langkah_kanan, regular, hantu_malam, nasib_baik,
//                     combo_master → combo, sharp_brain → otak_cerdas. tapah_ranger and raja_sapot
//                     no longer exist.
//   points            rows with no vendorID (old game and mission points) are removed: games and
//                     missions give XP now. Every row tied to a shop stays, so shop balances don't move.
//   games             gamePointsRewarded is renamed gameXpRewarded (0: those plays paid points, not XP).
//   vendors           shops without pointsPerVisit get the default 10.
//   missions, userMissions, userMissionTracking, shopVisits   old nodes, removed.
//
// Running it again is safe: anything already in the new shape is left alone.

const fs = require("fs");
const admin = require("firebase-admin");
const { databaseUrl, VISIT_XP, rankFor, weekKey } = require("./shared");

const DEFAULT_POINTS_PER_VISIT = 10; // AppConfig.DEFAULT_POINTS_PER_VISIT
const OLD_NODES = ["missions", "userMissions", "userMissionTracking", "shopVisits"];
const BADGE_MAP = {
  langkah_kanan: "langkah_kanan",
  regular: "regular",
  hantu_malam: "hantu_malam",
  nasib_baik: "nasib_baik",
  combo_master: "combo",
  sharp_brain: "otak_cerdas",
};
const BATCH = 400; // paths per multi-path update

function fail(message) {
  console.error("\n" + message + "\n");
  process.exit(1);
}

const val = async (db, path) => (await db.ref(path).once("value")).val() || {};

async function main() {
  const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!keyPath || !fs.existsSync(keyPath)) {
    fail("Set GOOGLE_APPLICATION_CREDENTIALS to the path of your service-account key file (see README.md).");
  }
  const key = JSON.parse(fs.readFileSync(keyPath, "utf8"));
  const DATABASE_URL = databaseUrl(key.project_id);
  admin.initializeApp({ credential: admin.credential.cert(key), databaseURL: DATABASE_URL });
  const db = admin.database();
  const apply = process.argv.includes("--yes");
  console.log(`Project:  ${key.project_id}`);
  console.log(`Database: ${DATABASE_URL}`);
  console.log(apply ? "Mode:     WRITING CHANGES\n" : "Mode:     dry run (nothing is written)\n");

  const [users, vendors, points, games, xpEvents, userStats, badges] = await Promise.all(
    ["users", "vendors", "points", "games", "xpEvents", "userStats", "badges"].map((n) => val(db, n))
  );
  const updates = {};
  const counts = {};
  const count = (what, n = 1) => { counts[what] = (counts[what] || 0) + n; };

  // Shop visits per customer, from the points ledger.
  const visitsBy = {};
  for (const [pointID, p] of Object.entries(points)) {
    if (!p || typeof p !== "object") continue;
    if (!p.vendorID) {
      updates[`points/${pointID}`] = null;
      count("points rows with no shop removed");
      continue;
    }
    if (p.pointSource === "shop_visit" && p.userID) (visitsBy[p.userID] = visitsBy[p.userID] || []).push({ pointID, ...p });
  }

  const now = Date.now();
  const thisWeek = weekKey(now);

  for (const [uid, u] of Object.entries(users)) {
    if (!u || typeof u !== "object") continue;
    for (const old of ["lifetimePoints", "currentTier", "unlockedBadges"]) {
      if (u[old] !== undefined) { updates[`users/${uid}/${old}`] = null; count(`users: ${old} removed`); }
    }
    if (u.role !== "customer") continue;

    const visits = (visitsBy[uid] || []).sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));
    if (u.xp === undefined) {
      const xp = visits.length * VISIT_XP;
      const rank = rankFor(xp).level;
      updates[`users/${uid}/xp`] = xp;
      updates[`users/${uid}/rank`] = rank;
      updates[`users/${uid}/rankSeen`] = rank;
      count("customers given XP and a rank");
    }
    const events = xpEvents[uid] || {};
    for (const v of visits) {
      if (!events[`visit_${v.pointID}`]) {
        updates[`xpEvents/${uid}/visit_${v.pointID}`] = { amount: VISIT_XP, source: "visit", timestamp: v.timestamp || now };
        count("visit XP events written");
      }
    }
    if (!userStats[uid] && visits.length > 0) {
      const shopsVisited = {};
      visits.forEach((v) => { shopsVisited[v.vendorID] = true; });
      updates[`userStats/${uid}`] = {
        totalVisits: visits.length,
        shopsVisited,
        weeksAllMissions: 0,
        lastVisitAt: visits[visits.length - 1].timestamp || now,
      };
      count("userStats written");
    }
    const week = visits.filter((v) => weekKey(v.timestamp || 0) === thisWeek);
    if (week.length > 0) {
      const xp = u.xp !== undefined ? u.xp : visits.length * VISIT_XP;
      updates[`weeklyVisits/${thisWeek}/${uid}`] = {
        visits: week.length,
        lastVisitAt: week[week.length - 1].timestamp,
        userName: u.userName || "",
        avatar: u.avatar || "",
        rank: rankFor(xp).level,
      };
      count("this week's leaderboard rows");
    }
    for (const [oldId, on] of Object.entries(u.unlockedBadges || {})) {
      const newId = BADGE_MAP[oldId];
      if (on && newId && !(badges[uid] && badges[uid][newId])) {
        updates[`badges/${uid}/${newId}/unlockedAt`] = now;
        count("old badges carried over (unclaimed)");
      }
    }
  }

  for (const [gameID, g] of Object.entries(games)) {
    if (g && g.gamePointsRewarded !== undefined) {
      updates[`games/${gameID}/gamePointsRewarded`] = null;
      if (g.gameXpRewarded === undefined) updates[`games/${gameID}/gameXpRewarded`] = 0;
      count("game rows renamed to gameXpRewarded");
    }
  }

  for (const [vendorID, v] of Object.entries(vendors)) {
    if (v && v.pointsPerVisit === undefined) {
      updates[`vendors/${vendorID}/pointsPerVisit`] = DEFAULT_POINTS_PER_VISIT;
      count(`shops set to ${DEFAULT_POINTS_PER_VISIT} points per visit`);
    }
  }

  for (const node of OLD_NODES) {
    const snap = await db.ref(node).once("value");
    if (snap.exists()) { updates[node] = null; count(`old node /${node} removed`); }
  }

  const paths = Object.keys(updates);
  if (paths.length === 0) {
    console.log("Nothing to change: the database is already in the new shape.");
    process.exit(0);
  }
  console.log("Changes:");
  for (const [what, n] of Object.entries(counts)) console.log(`  ${String(n).padStart(6)}  ${what}`);
  console.log(`  ${String(paths.length).padStart(6)}  paths in total\n`);

  if (!apply) {
    console.log("Nothing written. Back up first (docs/SETUP.md), then run:  npm run migrate -- --yes");
    process.exit(0);
  }
  for (let i = 0; i < paths.length; i += BATCH) {
    const chunk = {};
    paths.slice(i, i + BATCH).forEach((p) => { chunk[p] = updates[p]; });
    await db.ref().update(chunk);
    console.log(`  wrote ${Math.min(i + BATCH, paths.length)} / ${paths.length}`);
  }
  console.log("\nDone. Next: deploy docs/firebase-rules.json (docs/SETUP.md, step 6).");
  process.exit(0);
}

main().catch((e) => fail(e.stack || String(e)));
