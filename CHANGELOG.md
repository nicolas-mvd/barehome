# Changelog

## 1.0 — Day/Night

- One sun/moon Day/Night button replaces the separate backlight −/+ selector
  and the local prototype's Restore button. Short OK switches modes.
- Hold OK to edit each mode's Backlight (5–100) and Picture Brightness (0–100)
  in one-point steps. Save applies and remembers both values; Cancel/Back
  discards edits.
- Night starts at 5 / 20. Day starts from the TV's current valid levels, or the
  earlier Night prototype's saved pair when upgrading in the original Picture
  mode. Keep customized presets across updates. Startup and resume do not
  reapply settings.
- Reuse the optional `WRITE_SECURE_SETTINGS` grant and exact CSV4K device gate.
  Require the original Picture mode and refuse changes under automatic backlight
  or ambient-light control. Attempt recovery after incomplete writes or saves.
- Keep one Activity and one Canvas View, with no service, polling, network, or
  runtime dependencies. Update the AI installation and picture-control guides.

On **2026-10-17**, the owner tested the Day/Night development build on the one
tested CSV4K and reported that it works. Separate installation, signature,
default-Home, unchanged startup levels, and header checks passed on
`0.3.2-modes-test` (code 7). All **35 automated tests** passed, including profile
scenarios. The owner report is not a separate scripted result for every editor,
persistence, migration, or recovery path. Version 1.0 is code 8; personal test
builds used custom app ordering, while the public build is alphabetical.

Earlier tests on the same TV confirmed visible backlight changes at 16/86 and
the fixed Night preset at 5/20, with native service readbacks. Only Movie mode
(9) was tested. Other TVs, firmware, Picture modes, HDR/Dolby Vision, and
persistence across a TV reboot remain untested. See [the test record](docs/BRIGHTNESS.md#what-was-tested).

## 0.3.1 — CSV4K backlight fix

- Replace the ineffective Android system brightness control with the tested
  CSV4K vendor backlight setting, `Settings.Global.picture_backlight`.
- Require the exact tested model/device/product identifiers. Other TVs show an
  unavailable value; no generic brightness fallback is used.
- Add optional one-time `WRITE_SECURE_SETTINGS` access through
  `./tv.sh backlight-permission`; retain `brightness-permission` as an alias.
  The previous `WRITE_SETTINGS` grant does not enable this backend.
- Refuse changes while automatic backlight or the ambient-light sensor is
  enabled. Keep the 5% step and 5% minimum, with no background service or polling.
- Document permission scope, enabling/revoking access, and hardware test limits.

Tested on **one physical CSV4K KTC/Motorola TV**, Android 11 / API 30, device
`zhongshan`, product `KTC_MOTOROLA_4K_SA_21V2`. On 2026-10-17 a local
`0.3.1-backlight-test` build with this backlight code visibly changed the panel
at 16% and 86%; the person watching confirmed a clear difference. The vendor
service returned success and read back both levels. The original 66% was
restored, Picture → Brightness stayed at 50, and Settings → Home round-trip
passed. Only Movie mode (9) was tested. Other TVs, HDR/Dolby Vision, other picture
modes/firmware, and backlight persistence across reboot remain untested. The
test build used personal app ordering; this release uses alphabetical ordering.

## 0.3.0 — First public beta

- Minimal text launcher with D-pad navigation, Settings, and source configuration.
- Optional system brightness controls in 5% steps, with a 5% minimum.
- Alphabetical app ordering by default; configure pinned apps in `Config.java`.
- AI installation guide, wireless/USB debugging instructions, and rollback workflow.
- Portable build and device helper scripts, with per-device Home backups.

Only the CSV4K running Android 11 has been tested on real hardware. Other TVs
may need different default-Home setup, and brightness support varies.

Known issue in 0.3.0, superseded by the CSV4K fix in 0.3.1: the user reported no
visible brightness change between 5% and 100%. Those controls changed Android's
setting without a visible panel response. The earlier claim that 0.3.0 changed
this TV's backlight was retracted.

## 0.2.0 — Local prototype

- Added brightness controls and permission handling.

## 0.1.0 — Local prototype

- Initial launcher; software rendering selected after measuring its memory use.
