# SapotLokal Brand Design

The one design system for every screen in the app: customer, shop owner and sign-in. It is taken from the customer app as it ships today (the `hu_*` tokens and `Theme.SapotLokal.Customer`). Anything still using the old `sapot_*` look is out of date and moves to this.

On Android, read px as dp for sizes and as sp for type.

---

## 1. The idea

A rewards wallet for the neighbourhood. White ground, ink text, one orange for actions, and every shop shown as its own bold colour card.

- **Orange means "do this".** Shop colours mean "whose points these are". Ink and greys mean everything else.
- **One question per screen.** One column, cards stacked, generous spacing, one main button per card.
- **Built for everyone.** Many customers and shop owners are older: high contrast, 48dp+ targets, type that follows the phone's font size.

---

## 2. Words

Copy is part of the design. These rules apply to every screen, dialog, toast and empty state, in English and Malay.

### 2.1 Copy rules

1. **No subheading if the heading already explains it.** "Missions" needs nothing under it. Only add a line when it tells the user something they can't see (a deadline, a cost, a reason).
2. **No repeated words.** If the heading says "Your rewards", the cards don't say "Reward". If a button says "Claim 50 XP", nothing next to it also says "50 XP".
3. **No jargon.** Write what a shopkeeper's mother would say. "Points at Kopi Kita", not "Vendor-scoped loyalty balance". No "ledger", "tier", "multiplier coefficient", "redeem flow", "gamification".
4. **No eyebrows or kickers** (small text above a heading). No all-caps. Sentence case everywhere.
5. **Buttons say what happens,** with the cost or gain when there is one: "Claim for 30 points", "Claim 40 XP", "Show my QR". Never "Submit", "OK", "Proceed".
6. **Numbers carry their unit:** "30 points", "120 XP", "Rank 3". Never a bare number where it could be either.
7. **Short.** Toasts and status lines are one line. Error messages say what to do next ("Check your connection and try again").
8. Every string exists in **English and Malay**. The Home greeting ("Selamat pagi") stays Malay in both.

### 2.2 Glossary (use these exact words)

| Concept | English | Malay | Notes |
|---|---|---|---|
| Shop currency | points | mata | Always belongs to one shop: "30 points at Kopi Kita". Spent on rewards. |
| Level currency | XP | XP | Never spent. Moves you up ranks. Never call it "points". |
| Level | Rank | Pangkat | "Rank 3 · Kaki Lokal". |
| Rank bonus | "3x points" | "3x mata" | Shown as "Your rank gives 1.5x points at every shop". Never "multiplier". |
| Shop reward (rule) | reward | ganjaran | What a shop offers. |
| Claimed reward | voucher | baucar | What the customer holds after claiming. |
| Weekly / lifetime goals | missions | misi | "This week" and "Lifetime" as section names. |
| Achievements | badges | lencana | |
| Ranking | leaderboard | papan pendahulu | Counts visits only. |
| Shop owner | shop owner | pemilik kedai | Never "vendor" or "merchant" in UI. |

---

## 3. Colour

| Token | Hex | Use |
|---|---|---|
| `hu_bg` | #FFFFFF | Page ground, cards, bottom bar |
| `hu_ink` | #1A1C21 | All main text, labels on orange, selected chips and segments |
| `hu_soft` | #5A606B | Secondary text. The lightest grey allowed for text |
| `hu_mute` | #9AA0AA | Ticks, dividers, idle icons. **Never text** |
| `hu_line` | #ECEEF2 | Hairline borders, bar tracks |
| `hu_fill` | #F4F5F7 | Inputs, unselected chips, segment track |
| `hu_accent` | #F5A524 | **Actions only**: main buttons, Scan ring, active tab, progress fill |
| `hu_accent_text` | #8A5700 | Orange used as text or an idle icon on white |
| `hu_accent_tint` | #FFF3DA | Pale orange wash behind icon tiles and soft pills |
| `hu_ok` / `hu_ok_tint` | #13734A / #E3F6EC | "Open now", success |
| `hu_danger` | #B3261E | Errors, destructive actions |
| `hu_ripple` | #1F1A1C21 | Press ripple |

**Shop palette** (one per shop, picked from the shop id, fixed forever, white text on top): pink #E0436A, indigo #4B4FC4, purple #7B4FD6, green #1A9E63, blue #2F6FDE, teal #0F8C8C, berry #A9367F, coral #E86A4A. No orange or yellow, so a shop never looks like a button.

**Rank colours** (rank badge and rank card only):

| Rank | Colour |
|---|---|
| 1 | `hu_soft` #5A606B |
| 2 | shop-green #1A9E63 |
| 3 | shop-blue #2F6FDE |
| 4 | shop-purple #7B4FD6 |
| 5 | ink #1A1C21 with an `hu_accent` ring |

### Colour rules
- **The one orange rule.** Orange is for actions and progress only. Never decoration, headings, or a shop's identity. Orange as text is always `hu_accent_text`.
- **The shop owns its colour.** Same colour on every screen. A shop's Ready pill is white with the shop colour as text, not orange.
- **Locked means grey.** Locked badges and missions not yet started use greyscale at 45% opacity. Nothing locked is ever orange.

---

## 4. Type

**Figtree** (bundled, OFL) for everything. Hierarchy comes from weight and size, never a second font.

| Style | Weight / size | Use |
|---|---|---|
| Big | 600 / 52, -0.03em | The one big number per screen (XP on Home and Play, points on a shop) |
| Screen | 800 / 28, -0.02em | Screen titles |
| Reward | 600 / 24 | Reward name on a reward card |
| Title | 700 / 18 (17 on tiles and rows) | Card titles, section headings |
| Body | 500 / 15, `hu_soft` | Supporting text; ink when it states a cost |
| Small | 500 / 14, `hu_soft` | Row and tile subtitles |
| Link | 700 / 15, `hu_accent_text` | Inline actions, 48dp hit area |
| Button | 800 / 16 | Filled buttons. Outlined: 700 / 15 |
| Label | 700 / 12 | Tab labels, tick labels |

Shop cards use extrabold on colour: name 18, points 22, initials 13, status 700 at 15.

All type is in sp and scales with the phone. Containers wrap, never clip.

---

## 5. Space, shape, depth

- **Layout:** one column, 20dp page edge, 10–16dp between cards, 22–26dp above a section heading. Tablets (600dp+): content centred, max 640dp wide.
- **Targets:** 48dp minimum; main buttons 52dp; chips 44dp; list rows 72–80dp.
- **Corners:** controls 14, white cards and rows 18, shop cards 20, reward cards 22, photos 16, pills 12, segment thumb 11, bars 4. Logos and Scan are circles.
- **Borders:** 1dp hairline on cards, 2dp on outlined buttons, 3dp orange on the Scan ring.
- **Depth:** flat. Only two things cast a shadow: shop cards (3dp) and the raised Scan / Show QR button (8dp).
- **Icons:** line icons, 2-unit stroke on a 24 grid, round caps. The kopi cup is the brand mark: outline = visit to go, filled = visit made.

### Logo and app icon
- **The mark** is the filled kopi-cup stamp (the same drawing as a "visit made" cup on a shop card) with two short lines of steam, in ink.
- **App icon:** the ink mark on a full Kopi Orange background (`ic_launcher_*`). The themed icon on Android 13+ uses the same cup in one colour.
- **In-app logo** (`ic_brand_logo`): the app icon in miniature, an orange rounded tile (12dp corners at 40dp) with the ink cup. Shown alone at 64dp on sign-in screens and 96dp on the splash, never inside another box.
- **Notification icon** (`ic_stat_notify`): the cup as a white silhouette; Android tints it orange.
- The logo is the **one place orange stands for identity** rather than an action. Don't recolour it, put it on a shop colour, or add a wordmark beside it inside the tile.

---

## 6. Components

### Buttons
- **Main:** orange fill, ink label, 800/16, 52dp, usually full width. One per card.
- **Outlined:** white, 2dp hairline, ink 700/15, 48dp. Secondary actions.
- **Text link:** `hu_accent_text` 700/15.
- **Destructive:** outlined with `hu_danger` label (log out, delete reward). Always confirmed in a dialog.
- **Pressed:** ink ripple at 12%. **Disabled:** `hu_fill` background, `hu_soft` label.

### Inputs (sign-in, sign-up, shop settings, reward editor)
- `hu_fill` background, 14dp corners, 52dp tall, 14dp side padding, ink text 16, `hu_soft` hint.
- Label sits above the field (Title 15/700 ink), never inside as the only label.
- **Focus:** 2dp `hu_ink` border. **Error:** 2dp `hu_danger` border and one line of `hu_danger` Small text below.
- Password fields have a show/hide eye icon.

### Chips and segments
- Chip: `hu_fill`, ink 700/15, 44dp, no icon. Selected: ink fill, white text.
- Segmented switch: `hu_fill` track, 4dp padding, equal segments, selected segment ink with white text.

### Cards and rows
- White card: 18dp corners, 1dp hairline, 16dp padding, no shadow.
- Tile (Play, Me, shop-owner home): 120dp minimum, 28dp `hu_accent_text` icon on an `hu_accent_tint` circle, Title + one Small line at most.
- Row: 72dp minimum, leading icon or thumbnail, Title, optional Small line, trailing chevron when it opens something.

### Shop card (signature)
The shop's colour, 20dp corners, 3dp lift. White round logo with initials in the shop colour, name and points in white extrabold, up to 8 kopi cups, one status line. Not ready: cups show the visits to the next reward. Ready: white Ready pill with the shop colour as text. Same card on Home, Shops and the shop page.

**Cups:** one cup = one visit at this customer's current rate (shop points per visit × rank bonus). A reward needing 50 points at 15 points per visit shows 4 cups.

### Reward card and reward row
- **Card:** 22dp, washed in the shop colour at about 13% alpha. Logo, shop name, cost; reward name at 24; one line saying what claiming spends; photo (140dp, 16dp corners) only if the shop uploaded one; one full-width orange button.
- **Row:** white 18dp card, 80dp: 56dp thumbnail, name 17, state line (`hu_accent_text` when good), optional compact orange button.

### Ticked progress bar
8dp, hairline track, orange fill, white notches at each threshold, Label text under the notches. Used for ranks (Home, Play, Me), reward costs on a shop, and mission progress.

### Rank card (new)
White card. Rank badge (48dp circle in the rank colour with the rank number), rank name (Title), the Big XP number, a ticked bar to the next rank, and one line: "250 XP to Kaki Lokal". At rank 5: "Top rank" and no bar. One line underneath: "Your rank gives 1.5x points at every shop".

### Mission row (new)
Row with a 40dp icon tile, mission name (Title 17), progress "2 of 3" (Small), a slim progress bar, and on the right either the XP reward ("40 XP", Small ink) or, when done, a compact orange "Claim 40 XP" button. Claimed: a `hu_ok` tick and "Done". No description line unless the name can't explain it.

### Badge tile and badge sheet (new)
- **Tile:** 3 or 4 per row on Me, square, badge art 56dp, name below (Label 12, wraps to 2 lines). Locked: greyscale at 45% opacity, small lock icon. Unlocked and unclaimed: full colour with an orange dot.
- **Badge sheet** (bottom sheet on tap): badge art 96dp, name (Screen 28), one line on how to earn it, progress when it has a count ("6 of 10 visits"), XP value. Unlocked and unclaimed: orange "Claim 50 XP". Claimed: "Earned on 12 Nov". Locked: no button.

### Leaderboard row (new)
Place number (Title, ink), avatar, name, visits ("12 visits", Small). Places 1–3 get a small rank-coloured medal. The signed-in customer's row is pinned at the bottom with an `hu_accent_tint` background if they're not in view.

### Dialogs and sheets
Prefer bottom sheets. White, 22dp top corners, 20dp padding, a heading, at most one body line, one main button, optional text-link "Cancel". Celebration sheets (rank up, badge unlocked, top 3) may use the kopi cup and confetti; still one button.

### Empty, loading, error
- **Empty:** a line icon, one sentence, one outlined button ("Find shops"). No heading if the sentence says it.
- **Loading:** orange circular spinner centred, or skeleton cards in `hu_fill`.
- **Error:** one `hu_danger` sentence + "Try again" outlined button.

---

## 7. Navigation

- **Bottom bar** (72dp, white, hairline on top): idle tabs `hu_soft` with Label text; active tab ink with a 28×3dp orange bar on top. A centre action is a raised 60dp white circle with a 3dp orange ring.
- **Customer tabs:** Home · Shops · **Scan** · Rewards · Play.
- **Shop owner tabs:** Home · Rewards · **Show QR** · Redeem · Shop.
- **Subpages:** back arrow, screen title, no bottom bar, no overflow menu.
- **Tablets (600dp+):** bottom bar becomes a 96dp start-edge rail with the centre action on top.
- **One entry point per destination.** Badges and settings live in Me. Missions, games and the leaderboard live in Play. Log out lives in Me (customer) or Shop (owner).

---

## 8. Do and don't

**Do**
- Keep orange for actions and progress.
- Give every shop its fixed colour.
- Make every claim button state what it costs or gives.
- Keep every target 48dp or more and let type scale.
- Say "points" for shops and "XP" for ranks, every time.

**Don't**
- Add a subheading that repeats or restates the heading.
- Use orange or yellow as a shop colour.
- Put shadows on anything but shop cards and the centre action button.
- Use `hu_mute` for text.
- Use "vendor", "merchant", "tier", "multiplier", "ledger" or "gamification" in the UI.
