# SapotLokal — ERD Deviations & Changes

> **Purpose:** Every deliberate deviation from the original ERD is recorded here.
> This ensures the documents and the app never drift apart (build guide Rule 10).
>
> Format: one section per change, with the build guide Open Decision number,
> the reason, and what was changed.

---

## Change 1 — `isActive` field added to `VOUCHER`

**Open Decision:** #11
**Phase added:** Phase 1 (data model)
**Status:** ✅ Approved (build guide explicitly requires this)

**Original ERD:** `VOUCHER` entity has no `isActive` field.

**Problem:** The Simple App Guide says a shop owner can "turn off" a reward rule.
Without an `isActive` field, the only way to turn off a rule is to delete it,
which would orphan any `VOUCHER_USER` records already created from that rule.

**Change:** Add `isActive: Boolean` to the `VOUCHER` entity (defaulting to `true`).

- Setting `isActive = false` hides the rule from new voucher creation.
- Existing `VOUCHER_USER` records remain valid and redeemable.
- Security Rules prevent customers from toggling this field.

---

## Change 2 — `pointsRequiredSnapshot` field added to `VOUCHER_USER`

**Open Decision:** #14
**Phase added:** Phase 1 (data model — proposed; confirmed implementation in Phase 5)
**Status:** ⏳ Proposed — developer must confirm before Phase 5 builds auto-voucher logic

**Original ERD:** `VOUCHER_USER` has no cost field. The `pointsRequired` sits only on
the parent `VOUCHER` (the reward rule).

**Problem:** If a shop owner edits a reward rule's `pointsRequired` after customers
have already earned vouchers from it, the cost of their existing vouchers would
silently change. A customer who earned a voucher when the rule cost 100 points
could find it now costs 200, or vice versa.

**Proposed change:** Copy `pointsRequired` from the rule onto each new `VOUCHER_USER`
as `pointsRequiredSnapshot` at the moment the voucher is created. Redemption uses
`pointsRequiredSnapshot`, not the current rule value.

**Developer action required:** Confirm this change before Phase 5 is built.

---

## Change 3 — QR scanning library changed from ZXing to ML Kit

**Open Decision:** N/A (not in build guide; raised during Phase 1 library selection)
**Phase added:** Phase 1
**Status:** ✅ Approved (developer confirmed in Phase 1 session)

**Original spec:** Build guide Section 2 lists "ZXing" for QR scan + generate.

**Problem:** `zxing-android-embedded` (the Android scanning wrapper) is in maintenance
mode with known camera API incompatibilities on Android 14+.

**Change:**

- **Scanning** → replaced with `com.google.mlkit:barcode-scanning:17.3.0`.
  ML Kit is actively maintained, works on Android 8+, and is free.
- **Generation** → `com.google.zxing:core:3.5.3` is retained. The core library
  (used only to draw QR Bitmaps into an ImageView) is unaffected by the camera
  API issues — it never touches the camera.

The term "ZXing" in the build guide now refers to the core generation library only.
