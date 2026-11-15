// Removes everything seed.js created, plus anything else tied to the seeded accounts or shops
// (for example points a real customer earned at a seeded shop, or a seeded shop's live QR codes).
//
//   npm run cleanup -- --yes
//
// Seeded ids all start with "seed_", so real accounts and real shops are left alone.
// Uses the same GOOGLE_APPLICATION_CREDENTIALS variable as seed.js.

const fs = require("fs");
const admin = require("firebase-admin");
const { databaseUrl } = require("./shared");

const PREFIX = "seed_";

function fail(message) {
  console.error("\n" + message + "\n");
  process.exit(1);
}

// Keys of /node whose own key starts with the prefix.
async function keysWithPrefix(db, node) {
  const snap = await db.ref(node).once("value");
  const out = [];
  snap.forEach((child) => {
    if (child.key.startsWith(PREFIX)) out.push(child.key);
  });
  return out;
}

// Keys of /node whose child [field] starts with the prefix.
async function keysByField(db, node, field) {
  const snap = await db.ref(node).orderByChild(field).startAt(PREFIX).endAt(PREFIX + "").once("value");
  const out = [];
  snap.forEach((child) => {
    out.push(child.key);
  });
  return out;
}

async function main() {
  const keyPath = process.env.GOOGLE_APPLICATION_CREDENTIALS;
  if (!keyPath || !fs.existsSync(keyPath)) {
    fail("Set GOOGLE_APPLICATION_CREDENTIALS to the path of your service-account key file (see README.md).");
  }
  const key = JSON.parse(fs.readFileSync(keyPath, "utf8"));
  const DATABASE_URL = databaseUrl(key.project_id);
  admin.initializeApp({ credential: admin.credential.cert(key), databaseURL: DATABASE_URL });
  const db = admin.database();

  console.log(`Project:  ${key.project_id}`);
  console.log(`Database: ${DATABASE_URL}`);
  console.log(`Will delete every seeded account and every record whose id, userID or vendorID starts with "${PREFIX}".`);
  if (!process.argv.includes("--yes")) {
    fail("Nothing deleted. Run again with --yes to go ahead:  npm run cleanup -- --yes");
  }

  const updates = {};
  const mark = (node, keys) => keys.forEach((k) => { updates[`${node}/${k}`] = null; });
  const count = {};

  // Records keyed by a seeded id.
  for (const node of [
    "users", "vendors", "vendorPhotos", "vouchers",
    "xpEvents", "userStats", "missionProgress", "badges",
  ]) {
    const keys = await keysWithPrefix(db, node);
    mark(node, keys);
    count[node] = keys.length;
  }

  // Leaderboard rows: /weeklyVisits/{weekKey}/{uid}.
  const weeks = await db.ref("weeklyVisits").once("value");
  let weeklyRows = 0;
  weeks.forEach((week) => {
    week.forEach((row) => {
      if (row.key.startsWith(PREFIX)) {
        updates[`weeklyVisits/${week.key}/${row.key}`] = null;
        weeklyRows++;
      }
    });
  });
  count.weeklyVisits = weeklyRows;

  // Records that point at a seeded user or shop.
  const byField = [
    ["points", "userID"], ["points", "vendorID"],
    ["voucherUsers", "userID"], ["voucherUsers", "vendorID"],
    ["games", "userID"],
    ["qrcodes", "vendorID"],
    ["shopNotifications", "vendorID"],
  ];
  for (const [node, field] of byField) {
    const keys = await keysByField(db, node, field);
    mark(node, keys);
    count[`${node} (by ${field})`] = keys.length;
  }

  await db.ref().update(updates);
  console.log("Database records removed:");
  Object.entries(count).forEach(([k, v]) => console.log(`  ${k}: ${v}`));

  // Auth accounts.
  let removed = 0;
  let pageToken;
  do {
    const page = await admin.auth().listUsers(1000, pageToken);
    const uids = page.users.map((u) => u.uid).filter((uid) => uid.startsWith(PREFIX));
    if (uids.length) {
      await admin.auth().deleteUsers(uids);
      removed += uids.length;
    }
    pageToken = page.pageToken;
  } while (pageToken);
  console.log(`Auth accounts removed: ${removed}`);

  console.log("\nDone. You can now delete your service-account key (see README.md).");
}

// firebase-admin keeps its database connection open, so exit explicitly once the work is done.
main()
  .then(() => process.exit(0))
  .catch((e) => {
    console.error(e);
    process.exit(1);
  });
