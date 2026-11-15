// Seeds SapotLokal with test data from seed.json (data shape: REFRACTOR.md §4).
//
//   npm run seed -- --yes
//
// Needs two environment variables (see README.md):
//   GOOGLE_APPLICATION_CREDENTIALS  path to your Firebase service-account key (a secret, never commit it)
//   SEED_PASSWORD                   the password every seeded account will use (6+ characters)
//
// Every id this script creates starts with "seed_", so cleanup.js can remove exactly this data.
// Running it twice is safe: the same ids are overwritten (timestamps move to "now").

const fs = require("fs");
const path = require("path");
const admin = require("firebase-admin");
const { databaseUrl, DAY, VISIT_XP, rankFor, weekKey } = require("./shared");

const VOUCHER_EXPIRY_DAYS = 30; // AppConfig.VOUCHER_EXPIRY_DAYS
const ALLOWED_POINTS_PER_VISIT = [5, 10, 15, 20, 25, 30, 40, 50]; // AppConfig.ALLOWED_POINTS_PER_VISIT
const shopPoints = (rate, rank) => Math.floor(rate * rank.bonus + 0.5); // AppConfig.shopPointsForVisit

function fail(message) {
  console.error("\n" + message + "\n");
  process.exit(1);
}

async function main() {
  const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  const password = process.env.SEED_PASSWORD;
  if (!keyPath || !fs.existsSync(keyPath)) {
    fail("Set GOOGLE_APPLICATION_CREDENTIALS to the path of your service-account key file (see README.md).");
  }
  if (!password || password.length < 6) {
    fail("Set SEED_PASSWORD to a password of at least 6 characters (see README.md).");
  }

  const seed = JSON.parse(fs.readFileSync(path.join(__dirname, "seed.json"), "utf8"));
  for (const v of seed.vendors) {
    if (!ALLOWED_POINTS_PER_VISIT.includes(v.pointsPerVisit)) fail(`Shop ${v.key}: pointsPerVisit must be one of ${ALLOWED_POINTS_PER_VISIT}.`);
  }
  const projectId = JSON.parse(fs.readFileSync(keyPath, "utf8")).project_id;
  const DATABASE_URL = databaseUrl(projectId);
  const credential = admin.credential.cert(JSON.parse(fs.readFileSync(keyPath, "utf8")));
  admin.initializeApp({ credential, databaseURL: DATABASE_URL });

  console.log(`Project:  ${projectId}`);
  console.log(`Database: ${DATABASE_URL}`);
  console.log(`Will create/update ${seed.vendors.length} shops, ${seed.rewards.length} rewards, ${seed.customers.length} customers.`);
  if (!process.argv.includes("--yes")) {
    fail("Nothing written. Run again with --yes to go ahead:  npm run seed -- --yes");
  }

  const now = Date.now();
  const domain = seed.emailDomain || "seed.test";

  // ── 1. Auth accounts ───────────────────────────────────────────────────────
  const people = [
    ...seed.vendors.map((v, i) => ({ uid: `seed_${v.key}`, email: `vendor${String(i + 1).padStart(2, "0")}@${domain}`, name: v.ownerName })),
    ...seed.customers.map((c, i) => ({ uid: `seed_${c.key}`, email: `customer${String(i + 1).padStart(2, "0")}@${domain}`, name: c.name })),
  ];
  for (const p of people) {
    try {
      await admin.auth().getUser(p.uid);
      await admin.auth().updateUser(p.uid, { email: p.email, password, displayName: p.name, emailVerified: true });
    } catch (e) {
      if (e.code !== "auth/user-not-found") throw e;
      await admin.auth().createUser({ uid: p.uid, email: p.email, password, displayName: p.name, emailVerified: true });
    }
  }
  console.log(`Auth accounts ready: ${people.length}`);

  // ── 2. Database records, written in one batch ──────────────────────────────
  const updates = {};
  const vendorByKey = Object.fromEntries(seed.vendors.map((v) => [v.key, v]));
  const rewardByKey = Object.fromEntries(seed.rewards.map((r) => [r.key, r]));

  seed.vendors.forEach((v, i) => {
    const uid = `seed_${v.key}`;
    updates[`users/${uid}`] = {
      userID: uid,
      userName: v.ownerName,
      userEmail: `vendor${String(i + 1).padStart(2, "0")}@${domain}`,
      role: "vendor",
    };
    updates[`vendors/${uid}`] = {
      vendorID: uid,
      userID: uid,
      vendorName: v.name,
      vendorCategory: v.category,
      vendorAddress: v.address,
      ssmNumber: v.ssm,
      vendorDescription: v.description,
      isOpen: v.isOpen,
      operatingHours: v.hours,
      lastStatusChangedAt: now,
      pointsPerVisit: v.pointsPerVisit,
    };
  });

  seed.rewards.forEach((r) => {
    const id = `seed_${r.key}`;
    updates[`vouchers/${id}`] = {
      voucherID: id,
      vendorID: `seed_${r.vendor}`,
      voucherName: r.name,
      voucherDescription: r.description,
      pointsRequired: r.points,
      isActive: r.active,
    };
  });

  let pointRows = 0;
  let voucherRows = 0;
  const weekly = {}; // weekKey -> uid -> { visits, lastVisitAt }
  const warnings = [];

  seed.customers.forEach((c, i) => {
    const uid = `seed_${c.key}`;
    const rank = rankFor(c.xp);
    const balance = {}; // vendor key -> shop points
    const shopsVisited = {};
    let totalVisits = 0;
    let firstVisitAt = 0;
    let lastVisitAt = 0;

    // Shop visits only: games, missions and bonuses give XP now, not shop points.
    c.points
      .filter((p) => p.source === "shop_visit" && p.vendor)
      .forEach((p, n) => {
        const vendor = vendorByKey[p.vendor];
        const pointID = `seed_p_${c.key}_${String(n + 1).padStart(2, "0")}`;
        const at = now - p.daysAgo * DAY;
        const earned = shopPoints(vendor.pointsPerVisit, rank);
        updates[`points/${pointID}`] = {
          pointID,
          userID: uid,
          vendorID: `seed_${p.vendor}`,
          pointEarnedAmount: earned,
          pointRedeemedAmount: 0,
          pointSource: "shop_visit",
          timestamp: at,
          basePoints: vendor.pointsPerVisit,
          rankBonus: rank.bonus,
        };
        updates[`xpEvents/${uid}/visit_${pointID}`] = { amount: VISIT_XP, source: "visit", timestamp: at };
        balance[p.vendor] = (balance[p.vendor] || 0) + earned;
        shopsVisited[`seed_${p.vendor}`] = true;
        totalVisits++;
        firstVisitAt = firstVisitAt ? Math.min(firstVisitAt, at) : at;
        lastVisitAt = Math.max(lastVisitAt, at);
        const wk = weekKey(at);
        const row = ((weekly[wk] = weekly[wk] || {})[uid] = weekly[wk][uid] || { visits: 0, lastVisitAt: 0 });
        row.visits++;
        row.lastVisitAt = Math.max(row.lastVisitAt, at);
        pointRows++;
      });

    let vouchersUsed = 0;
    c.claims.forEach((claim, n) => {
      const reward = rewardByKey[claim.reward];
      const tag = `${c.key}_${String(n + 1).padStart(2, "0")}`;
      const claimedAt = now - claim.daysAgo * DAY;
      const claimPointID = `seed_p_${tag}_claim`;
      const voucherUserID = `seed_vu_${tag}`;
      balance[reward.vendor] = (balance[reward.vendor] || 0) - reward.points;
      if (balance[reward.vendor] < 0) warnings.push(`${c.name} spends more than they earned at ${vendorByKey[reward.vendor].name}.`);
      // Points are spent when a voucher is claimed, so every claim has a matching spend row.
      updates[`points/${claimPointID}`] = {
        pointID: claimPointID,
        userID: uid,
        vendorID: `seed_${reward.vendor}`,
        pointEarnedAmount: 0,
        pointRedeemedAmount: reward.points,
        pointSource: "redemption",
        timestamp: claimedAt,
      };
      updates[`voucherUsers/${voucherUserID}`] = {
        voucherUserID,
        userID: uid,
        voucherID: `seed_${reward.key}`,
        vendorID: `seed_${reward.vendor}`,
        // Looks like the app's own codes: SAPOT- plus 6 characters (customer number, claim number, reward number).
        voucherCode: `SAPOT-S${String(i + 1).padStart(2, "0")}${n + 1}${reward.key.slice(1)}`,
        voucherStatus: claim.status === "used" ? "used" : claim.status === "expired" ? "expired" : "active",
        voucherExpiryDate: claimedAt + VOUCHER_EXPIRY_DAYS * DAY,
        pointsRequiredSnapshot: reward.points,
        voucherTitle: reward.name,
        createdAt: claimedAt,
        pointsDeducted: true,
        claimPointID,
      };
      if (claim.status === "used") vouchersUsed++;
      pointRows++;
      voucherRows++;
    });

    if (c.xp < totalVisits * VISIT_XP) warnings.push(`${c.name}: xp ${c.xp} is less than their ${totalVisits} visits are worth.`);

    updates[`users/${uid}`] = {
      userID: uid,
      userName: c.name,
      userEmail: `customer${String(i + 1).padStart(2, "0")}@${domain}`,
      role: "customer",
      xp: c.xp,
      rank: rank.level,
      ...(c.avatar ? { avatar: c.avatar } : {}),
    };
    updates[`userStats/${uid}`] = { totalVisits, shopsVisited, weeksAllMissions: 0, lastVisitAt };

    // Badges the data already earns; their XP waits to be claimed in the app.
    if (totalVisits > 0) updates[`badges/${uid}/langkah_kanan`] = { unlockedAt: firstVisitAt };
    if (vouchersUsed > 0) updates[`badges/${uid}/belanja_diri`] = { unlockedAt: now - c.claims.find((x) => x.status === "used").daysAgo * DAY };

    c._rank = rank.level;
  });

  // Leaderboard source for every week that has visits.
  const customerByUid = Object.fromEntries(seed.customers.map((c) => [`seed_${c.key}`, c]));
  for (const [wk, rows] of Object.entries(weekly)) {
    for (const [uid, row] of Object.entries(rows)) {
      const c = customerByUid[uid];
      updates[`weeklyVisits/${wk}/${uid}`] = { ...row, userName: c.name, avatar: c.avatar || "", rank: c._rank };
    }
  }

  await admin.database().ref().update(updates);
  console.log(`Database written: ${seed.vendors.length} shops, ${seed.rewards.length} rewards, ${pointRows} point rows, ${voucherRows} vouchers, ${Object.keys(weekly).length} leaderboard weeks.`);

  const thisWeek = weekKey(now);
  const lastWeek = weekKey(now - 7 * DAY);
  for (const [label, wk] of [["This week", thisWeek], ["Last week", lastWeek]]) {
    const top = Object.entries(weekly[wk] || {})
      .sort((a, b) => b[1].visits - a[1].visits || a[1].lastVisitAt - b[1].lastVisitAt)
      .slice(0, 3)
      .map(([uid, r], n) => `#${n + 1} ${customerByUid[uid].name} (${r.visits})`);
    console.log(`${label} (${wk}) top 3: ${top.join(", ") || "nobody"}`);
  }
  if (warnings.length) console.log("\nCheck seed.json:\n  " + warnings.join("\n  "));

  console.log("\nLogins (all use your SEED_PASSWORD):");
  people.forEach((p) => console.log(`  ${p.email}`));
  console.log("\nDone. Remove it all later with:  npm run cleanup -- --yes");
}

// firebase-admin keeps its database connection open, so exit explicitly once the work is done.
main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error(e);
    process.exit(1);
  });
