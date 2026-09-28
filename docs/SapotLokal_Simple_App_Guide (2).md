# SapotLokal — Simple App Guide

This document explains the SapotLokal app in plain language: what it does, how people use it, what tools will build it, and how to build it step by step. No technical jargon.

---

## 1. What is SapotLokal?

SapotLokal is a mobile app that rewards people for supporting local shops.

There are two types of people who use the app:

1. **Customers** — regular people who visit local shops and earn points.
2. **Shop Owners (Vendors)** — the local businesses that give out points and give rewards.

Customers earn points by visiting shops and playing simple games in the app. They then trade those points for rewards, like a free coffee or a discount voucher.

---

## 2. How the App Works (Step by Step Story)

### When someone opens the app for the first time
1. They see a **welcome screen** (logo, loading).
2. They tap **"Sign Up"** and are first asked: **"Are you signing up as a Customer or a Shop Owner?"**
3. **If Customer:** they sign up with just their name, email, and password — normal, no extra requirements.
4. **If Shop Owner:** they sign up with their name, email, and password, **plus their SSM Registration Number** (the official number every legal business in Malaysia has, from Suruhanjaya Syarikat Malaysia).
   - The app simply requires this field to be filled in before the account can be created — it's stored as part of their shop profile, the same way their shop name or address is.
   - The app does **not** check this number against any government database. Verifying it that way would mean connecting to an official record, which usually needs a formal agreement and comes with legal/privacy responsibilities that are too much for this stage.
   - The point of asking for it here isn't to be a legal checkpoint — it's just a light filter: someone has to know their own SSM number to type it in, which already discourages a random customer from casually picking "Shop Owner." If stronger verification is ever needed later, it can be added as a separate step (e.g. a human admin reviewing new shop sign-ups), rather than an automatic database check.
5. Once signed up, they simply **log in** with their email and password like normal — the app already knows from their account whether they're a Customer or Shop Owner, so they don't need to choose again every time.

### If they are a Customer
1. They land on their **Home Screen**, which shows their points, their level, and a progress bar.
2. From Home, they can go to:
   - **Find Shops** — a list of local shops they can visit.
   - **Challenges** — small tasks they can complete for bonus points (e.g. "Visit 5 shops this week").
   - **Rewards / My Vouchers** — a page where they see any vouchers they've already unlocked, grouped by shop, ready to use next time they visit.
3. **Earning points by visiting a shop:**
   - The customer taps "Scan" on their Home Screen.
   - Their phone camera opens.
   - They point the camera at a QR code shown on the shop's screen or counter.
   - The app checks if the code is valid, then adds points to their account.
   - A little animation plays ("+20 points!") and their progress bar fills up.
4. **Earning points by playing games:**
   - **Spin the Wheel** — once a day, they can spin a wheel for a random number of points.
   - **Memory Match** — a simple card-flipping game with shop logos; matching pairs earns points.
5. **Earning vouchers automatically:**
   - Each shop can set up more than one reward tier (e.g. Shop A might have "100 points = 10% Discount," "200 points = Free Cookie," and "350 points = Free Meal").
   - The customer doesn't need to manually "redeem" anything — as soon as their points for that shop reach **any** of that shop's thresholds, the app **automatically creates a voucher** for that reward, with its own unique QR code.
   - If a customer has enough points to qualify for more than one reward at the same shop, **all the rewards they qualify for show up** in "My Vouchers" — the app doesn't force them to pick just one. The highest reward they qualify for is labeled **"Best Choice"** so they know which one gives them the most value, but the decision on which voucher to actually use is entirely up to them.
   - This voucher is saved under "My Vouchers" and is permanently tied to that one shop.
6. **Using the voucher:**
   - Next time the customer visits that shop, they open their voucher and show the QR code on their phone screen.
   - The shop owner scans it with their app's scanner. If the camera doesn't work for some reason, they can also **type in the voucher's code by hand** as a backup.
   - The app checks the database to confirm three things: the code is real, it belongs to that specific shop, and it hasn't already been used.
   - If everything checks out, the app tells the shop owner exactly what reward to give (e.g. "Give: Free Cake") — the shop owner doesn't need to remember their own reward rules, the app remembers for them.
   - The app then marks the voucher as "Used" so it can never be scanned again.

### If they are a Shop Owner
1. They land on their **Shop Home Screen**.
2. **Giving out points:**
   - The shop owner opens "Show QR Code" on their screen.
   - This code automatically changes every 60 seconds (like a rotating password), so customers can't reuse an old screenshot to cheat.
   - Customers scan this code with their phones to earn points.
3. **Setting up their own reward rules:**
   - Each shop can set up as many reward rules as they want, not just one. For example, Shop A could set:
     - 100 points → 10% Discount
     - 200 points → Free Dubai Chewy Cookie
     - 350 points → Free Meal
   - Every rule is its own points-to-reward pairing. The shop owner sets each one up **once** on their **"Reward Rules"** page — they never have to manually create a voucher for each customer.
   - They can add, edit, or turn off a rule anytime — for example, running a special promo for a limited time.
   - This rule is what the app uses later to automatically create vouchers for customers, and to tell the shop owner what to give when a voucher is scanned.
4. **Redeeming a customer's voucher:**
   - When a customer shows their voucher QR code, the shop owner taps "Scan to Redeem."
   - If the camera can't read it, the shop owner can type in the voucher's code by hand instead.
   - The app checks that the voucher is valid, belongs to this shop, and hasn't been used already.
   - The customer chooses which voucher to use if they have more than one available — the app just looks up whichever one gets scanned, and shows the shop owner exactly what to give (using that reward's rule), then marks the voucher as "Used" so it can't be scanned again.
5. **Managing their shop:**
   - The shop owner can edit their shop's name, category, address, and logo in "Shop Settings."

---

## 3. The Two Codes That Make It Work

There are two different QR codes in the app — it's easy to mix them up, so here's the difference:

| Code | Shown by | Scanned by | Purpose |
|---|---|---|---|
| **Shop Code** | Shop Owner (on their screen) | Customer | Gives the customer points for visiting |
| **Voucher Code** | Customer (on their screen) | Shop Owner | Lets the customer claim a reward they've already unlocked by reaching that shop's point target — scan with the camera, or the shop owner can type the code in by hand if scanning fails |

---

## 4. What Tools Will Be Used to Build It

Simple explanation of the "ingredients" for building this app:

| Part of the App | Tool Used | What It Does, in Plain Terms |
|---|---|---|
| Phone type | Android (8.0 or newer) | The app is built for Android phones — works on most phones from the last several years. |
| Look and feel | Google Material Design | The clean, modern style used in most well-known Android apps — rounded buttons, simple cards, familiar icons. |
| Accounts & login | Firebase Auth | Handles sign-up and login safely, so passwords and accounts are managed securely. |
| Live data storage | Firebase Realtime Database | Stores everyone's points, levels, vouchers, and rewards, and keeps everything updated instantly across all phones. |
| Notifications | Firebase Cloud Messaging | Sends alerts like "You leveled up!" or "Your reward is ready." |
| QR code scanning & generating | ZXing | A free, widely-used tool that handles the camera and reads/creates QR codes. |
| App logic (what happens when you tap things) | Java | The programming language used to make buttons, screens, and features actually work. |
| App layout (how things look on screen) | XML | Used to arrange buttons, text, and images on each screen. |
| Design mockups | Figma | Where each screen is designed and previewed before it's built. |
| Code storage & backups | GitHub | Keeps all the app's code saved and tracked, so no work is ever lost. |

In short: it's a standard, well-supported Android app setup — nothing exotic, all tools that are free, popular, and well-documented.

---

## 5. How the Points and Rewards Are Kept Fair

Some simple rules to prevent cheating, explained in plain terms:

- The app doesn't verify SSM numbers against a live authority, but simply requiring the field means a random customer can't casually pretend to be a shop owner — a heavier verification step (like manual admin review) can be layered on later without needing to touch any government database.
- The shop's QR code **changes every 60 seconds**, so someone can't take a photo of it and reuse it forever.
- A voucher **can only be scanned once** — once a shop owner redeems it, it's marked "used" and can't be reused.
- A voucher only works at the **specific shop it was earned at** — a voucher from Shop A can't be used at Shop B.
- Vouchers are created **automatically by the app** the moment a customer's points cross any of a shop's thresholds, so there's no manual step where mistakes could happen.
- If a customer qualifies for several rewards from one shop at once, they get to see and keep **all** of them — the app won't quietly pick one for them or throw the others away.
- The app double-checks everything against the live database, not just what's stored on the phone, so people can't trick it by editing their own phone's data.

---

## 6. Suggested Order to Build the App

You don't need to build everything at once. Here's a sensible order:

**Step 1 — The Basics**
- Set up the account system (sign up, log in), with the Customer/Shop Owner choice at sign-up.
- Add the SSM Registration Number as a required text field on Shop Owner sign-up (just collected and stored — no external verification).
- Build the welcome screen and login screen.

**Step 2 — Customer Side, Core Loop**
- Build the Customer Home Screen.
- Build the "Find Shops" list.
- Build the camera scanner and connect it so scanning a shop code gives points.

**Step 3 — Shop Owner Side, Core Loop**
- Build the Shop Owner Home Screen.
- Build the rotating QR code screen.
- Build the "Reward Rules" page, so shops can set up multiple points-to-reward tiers.
- Build the scanner (plus manual code entry backup) shop owners use to redeem vouchers.

**Step 4 — Rewards & Vouchers**
- Connect points earned at a shop to that shop's reward rules, so hitting **any** threshold automatically creates a matching voucher.
- Build "My Vouchers" for customers, showing every voucher they currently qualify for, with the highest-value one labeled "Best Choice."
- Make sure a scanned/entered voucher is checked against the database (real, right shop, not used yet) before the shop owner sees what to give.

**Step 5 — Games & Levels**
- Build the points progress bar and "leveling up" feeling.
- Build Spin the Wheel.
- Build the Memory Match game.

**Step 6 — Extra Engagement**
- Build the Challenges list.
- Turn on push notifications for things like level-ups and completed challenges.
- Build the Shop Settings/profile page.

**Step 7 — Polish**
- Test what happens with expired codes, already-used vouchers, and no internet connection.
- Make sure everything looks clean and matches the Figma designs.
- Fix bugs and smooth out the experience.

---

## 7. Things to Decide Before or While Building

A few small decisions to make (can be adjusted anytime, these are just starting points):

- How many points does a shop visit give? *(e.g. 20 points per visit)*
- How many points does winning Spin the Wheel or Memory Match give?
- Does every shop need at least one reward rule before they can go live, or can they start with none?
- When a customer qualifies for multiple rewards from the same shop at once, do they keep all the vouchers until used, or does claiming one remove the others? *(Current plan: keep all, mark the highest as "Best Choice," let the customer decide.)*
- After a customer uses a voucher, does their points counter for that shop reset to 0, or keep accumulating toward the next threshold above the one they just claimed?
- How long does a voucher last before it expires?
- Does a new shop owner need approval before they can join, or can they sign up freely?
- If stronger vendor verification is wanted down the line, would that be a manual review (a person checking) rather than an automatic database check?

---

*This document is meant to describe how the app should feel and work, in plain language, so it can be used to guide building the app from scratch.*
