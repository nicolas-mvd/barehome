# Changelog

## 0.3.0 — First public beta

- Minimal text launcher with D-pad navigation, Settings, and source configuration.
- Optional system brightness controls in 5% steps, with a 5% minimum.
- Alphabetical app ordering by default; configure pinned apps in `Config.java`.
- AI installation guide, wireless/USB debugging instructions, and rollback workflow.
- Portable build and device helper scripts, with per-device Home backups.

Only the CSV4K running Android 11 has been tested on real hardware. Other TVs
may need different default-Home setup, and brightness support varies.

Known issue: the CSV4K user reports no visible brightness change between 5% and
100%. The controls change Android's setting, but panel control is not verified.
The earlier claim that they changed this TV's backlight has been retracted.

## 0.2.0 — Local prototype

- Added brightness controls and permission handling.

## 0.1.0 — Local prototype

- Initial launcher; software rendering selected after measuring its memory use.
