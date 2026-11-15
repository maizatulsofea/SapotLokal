# SapotLokal data scripts

Three scripts that talk to your Firebase project with admin access:

| Script | Does |
|---|---|
| `npm run migrate` | Moves data from the old app version to the current database shape. See [docs/SETUP.md](../../docs/SETUP.md) step 5. |
| `npm run seed` | Adds test data (below). |
| `npm run cleanup` | Removes the test data. |

All three print the project and database they will use, and write nothing until you add `-- --yes`.

## Seed data

Creates test data in your Firebase project: **10 shops, 15 rewards and 10 customers**, with logins, shop visits,
XP across all 5 ranks, weekly leaderboard rows and a few claimed vouchers. One command removes it all again.

Everything the seeder creates has an id starting with `seed_`, so cleanup never touches real accounts.

## One-time setup

1. Install **Node.js 18 or newer** from https://nodejs.org (the LTS version is fine). Open a new PowerShell window afterwards.
2. In PowerShell:

   ```powershell
   cd C:\Projects\SapotLokal\tools\seed
   npm install
   ```

3. Get a **service-account key**. It is a secret that gives full access to your Firebase project, so keep it out of git
   (this folder's `.gitignore` already ignores it) and never share it.
   - Firebase Console > Project settings (gear icon) > **Service accounts** > **Generate new private key**.
   - Save the file as `C:\Projects\SapotLokal\tools\seed\serviceAccountKey.json`.
4. The scripts find your database from the key's project id, assuming the **asia-southeast1** location. If your
   database is somewhere else, set its address first (Realtime Database > Data tab, the link at the top):

   ```powershell
   $env:DATABASE_URL = "https://<your-database-address>"
   ```

## Seed

```powershell
cd C:\Projects\SapotLokal\tools\seed
$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\Projects\SapotLokal\tools\seed\serviceAccountKey.json"
$env:SEED_PASSWORD = "choose-a-password-6+"
npm run seed -- --yes
```

Without `--yes` it only prints what it would do. The password is only used for this run: it is never saved in a file.

## Logins

All use the password you set in `SEED_PASSWORD`.

**Shop owners:** `vendor01@seed.test` … `vendor10@seed.test` own, in order:

| # | Shop | Points per visit |
|---|---|---|
| 01 | Kopi Kita Cafe | 10 |
| 02 | Roti Segar Bakery | 15 |
| 03 | Warung Mak Cik | 5 |
| 04 | Segar Mart (starts closed) | 20 |
| 05 | Bunga Florist | 10 |
| 06 | Buku Lokal | 10 |
| 07 | Gaya Salon (starts closed) | 25 |
| 08 | Manis Dessert | 15 |
| 09 | Jahit Pantas Tailor | 30 |
| 10 | Ping Hardware | 50 |

**Customers** (rank = XP ladder in AppConfig.Rank):

| Email | Who | XP · rank | Test it for |
|---|---|---|---|
| `customer01@seed.test` | Aisyah | 0 · 1 | Brand new: no visits, no points |
| `customer02@seed.test` | Daniel | 150 · 1 | Small balances at two shops |
| `customer03@seed.test` | Farah | 520 · 2 | 39 points at Kopi Kita (enough for one reward); last week #3 |
| `customer04@seed.test` | Kumar | 980 · 2 | Active voucher at Kopi Kita; 126 points at Ping Hardware |
| `customer05@seed.test` | Mei Ling | 1,650 · 3 | One used and one expired voucher; `belanja_diri` badge unlocked |
| `customer06@seed.test` | Hakim | 260 · 1 | Two recent visits at Manis Dessert |
| `customer07@seed.test` | Siti | 4,100 · 4 | Most visits (12); this week #1; last week #2 |
| `customer08@seed.test` | Raj | 2,300 · 3 | One shop only (Buku Lokal, 75 points) |
| `customer09@seed.test` | Nurul | 7,300 · 5 | Top rank (3x points) with no visits yet: her first scan shows the bonus |
| `customer10@seed.test` | Chong Wei | 3,600 · 4 | Balances at four shops, an active voucher; **last week #1 with an unclaimed top-3 reward** |

Every customer with a visit has the `langkah_kanan` badge unlocked but not claimed.

Notes:
- Rewards: Gaya Salon's "Hair wash" is switched off on purpose.
- Timestamps are relative ("3 days ago"), so the data looks current whenever you seed. The leaderboard weeks move with
  them: the script prints this week's and last week's top 3 when it finishes.
- Shop points for each visit are worked out the way the app does: the shop's points per visit × the customer's rank bonus.
- XP above what a customer's visits are worth is a starting balance with no event rows behind it.
- Shop photos are not seeded; shop cards show the shop's colour and initials. Add real photos from the shop-owner app (Shop tab).
- A shop's QR code is created only while its owner has the app open, as usual.
- Re-running `seed` is safe. It overwrites the same ids and refreshes the timestamps.

## Migrate old data

```powershell
cd C:\Projects\SapotLokal\tools\seed
$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\Projects\SapotLokal\tools\seed\serviceAccountKey.json"
npm run migrate            # dry run: prints what would change
npm run migrate -- --yes   # makes the changes (back up first!)
```

The full list of changes is at the top of `migrate.js`. It keeps every account, shop, reward, voucher and shop-points
balance, and is safe to run twice.

## Change the data

Edit `seed.json`, then run `seed` again.
- A shop has `pointsPerVisit` (5, 10, 15, 20, 25, 30, 40 or 50).
- A customer has `xp` (their rank comes from it) and visits like `{ "vendor": "v01", "source": "shop_visit", "daysAgo": 3 }`.
  Rows with any other source are ignored: games, missions and bonuses give XP now, not shop points.
- A claim is `{ "reward": "r01", "status": "active", "daysAgo": 2 }` (status: `active`, `used` or `expired`); it also
  writes the matching spend row, because points are spent when a voucher is claimed. The script warns if a claim costs
  more than the customer earned at that shop.

## Remove the seed data

```powershell
cd C:\Projects\SapotLokal\tools\seed
$env:GOOGLE_APPLICATION_CREDENTIALS = "C:\Projects\SapotLokal\tools\seed\serviceAccountKey.json"
npm run cleanup -- --yes
```

This deletes the seeded Auth accounts and every database record tied to a seeded account or shop. That includes points a
real customer earned at a seeded shop and vouchers they claimed there. Real accounts and real shops are not touched.

## When you are finished

Delete `serviceAccountKey.json` and, in the Firebase Console (Project settings > Service accounts), delete the key too.
The seeder writes with admin access, so it skips your security rules. After seeding, also sign in as a seeded customer and
vendor in the app to check the rules allow what the app does.
