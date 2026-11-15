// Shared by seed.js, cleanup.js and migrate.js: which database to use, and the app's economy
// numbers they need (keep these in step with AppConfig.kt).
//
// Which database:
// Set DATABASE_URL to your database's address (Firebase Console > Realtime Database, the link at
// the top of the Data tab). Without it, the address is built from the service-account key's project
// id for a database in asia-southeast1 (Singapore), which is what SapotLokal uses.

function databaseUrl(projectId) {
  const fromEnv = (process.env.DATABASE_URL || "").trim().replace(/\/+$/, "");
  if (fromEnv) return fromEnv;
  return `https://${projectId}-default-rtdb.asia-southeast1.firebasedatabase.app`;
}

const DAY = 24 * 60 * 60 * 1000;
const KL_OFFSET = 8 * 60 * 60 * 1000; // Asia/Kuala_Lumpur is UTC+8 all year
const VISIT_XP = 15; // AppConfig.VISIT_XP

// AppConfig.Rank: minimum XP and shop-points bonus for ranks 1–5.
const RANKS = [
  { level: 1, minXp: 0, bonus: 1.0 },
  { level: 2, minXp: 400, bonus: 1.25 },
  { level: 3, minXp: 1500, bonus: 1.5 },
  { level: 4, minXp: 3500, bonus: 2.0 },
  { level: 5, minXp: 7000, bonus: 3.0 },
];
const rankFor = (xp) => RANKS.filter((r) => xp >= r.minXp).pop();

// ISO week key in Malaysia time, e.g. "2026-W46" (same as MissionDateUtils.getWeekYearKey).
function weekKey(ms) {
  const d = new Date(ms + KL_OFFSET);
  const day = (d.getUTCDay() + 6) % 7; // Monday = 0
  const thursday = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() - day + 3));
  const firstThursday = new Date(Date.UTC(thursday.getUTCFullYear(), 0, 4));
  const week = 1 + Math.round(((thursday - firstThursday) / DAY - 3 + ((firstThursday.getUTCDay() + 6) % 7)) / 7);
  return `${thursday.getUTCFullYear()}-W${String(week).padStart(2, "0")}`;
}

module.exports = { databaseUrl, DAY, KL_OFFSET, VISIT_XP, RANKS, rankFor, weekKey };
