# BareHome: instructions for AI assistants

BareHome is a small Android TV launcher. A common user request is “I found this
repo and want it on my TV.” Carry that through connection, installation, testing,
default Home selection, and a usable rollback path. Read [README.md](README.md),
[docs/CONNECT.md](docs/CONNECT.md), and [docs/BRIGHTNESS.md](docs/BRIGHTNESS.md)
before operating a device.

## Establish access and scope

- Determine whether your tools can execute on the user's computer and reach the
  TV's local network. A cloud shell or ordinary ChatGPT web session is not the
  user's computer. If local access is unavailable, guide the user through the
  documented commands and work from the relevant output; do not claim to have
  connected or installed anything yourself.
- Use official Android SDK Platform Tools. Reuse working ADB instead of replacing
  the user's installation. Scripts accept `BAREHOME_ADB` for its location.
- Follow the connection method actually offered by the TV. Wireless pairing,
  vendor network debugging, and USB debugging are distinct. Never infer a network
  listener merely from a USB debugging switch. Use a TV address supplied by the
  user or established by the TV's settings; do not scan unrelated devices.
- Ask only for information or physical actions you cannot obtain yourself:
  opening Developer options, accepting the TV's authorization prompt, or choosing
  the intended TV if several are plausible. Enter pairing codes through the ADB
  prompt; do not put them in committed files, logs, or command arguments.
- Run `adb devices -l`, select exactly one serial, and use `adb -s SERIAL` for
  every device operation. Check model, Android version/API, and current user.
  Require Android TV/Google TV and API 26+. The helper targets Android user 0;
  if another user is active, adapt explicitly for that user before proceeding.

An instruction to install BareHome as the user's launcher authorizes the normal
reversible installation and default-Home steps. Do not repeatedly ask permission
for each command. Optional picture access is covered when the user requested working
backlight/Day/Night controls or used the README's installation prompt. Explain
the broader permission scope before granting it as part of those steps. Do not
interpret an installation request as permission to erase data or reconfigure
unrelated apps.

## Install and verify

1. Inspect the repository's scripts before executing them. Obtain the published
   `BareHome.apk` from this repository's latest release, checking its supplied
   checksum, or build from source if the user wants customization. Do not obtain
   an APK from an unrelated mirror.
2. Set `BAREHOME_TV` to the verified serial and run `./tv.sh status`.
   **Before installing a new Home app**, resolve the existing default Home and
   save its exact component for rollback. Installation can reset Home resolution
   to a chooser. Use `./tv.sh capture-home`; do not overwrite a valid saved
   original with BareHome, a resolver, or a chooser. If no actual launcher is
   resolved, establish a known working Home and save it first.
3. Run `./tv.sh install /path/to/BareHome.apk`. Installation uses `-r` to preserve
   existing BareHome data. If a signature mismatch occurs, stop and explain the
   release-key versus local-key distinction. Do not silently uninstall it.
4. Run `./tv.sh run`. Confirm the activity is displayed and responsive. Check
   D-pad navigation, an app launch, Settings, and returning to BareHome. Keep
   checks brief and avoid starting playback or changing unrelated settings.
5. Use `./tv.sh use` once the preview works and a valid prior Home is saved.
   Verify Home resolves to `uy.nico.barehome/.MainActivity` (the equivalent fully
   qualified component is also valid), and press the remote's Home key or send
   `KEYCODE_HOME`. Confirm the displayed app. A successful ADB connection alone
   is not successful installation or default selection.
6. If requested, enable and test Day/Night picture controls as below. Provide the installed
   version, what was verified, any remaining compatibility limits, and the
   exact rollback command. Explain that ordinary use needs neither ADB nor AI.
7. Help the user turn debugging off afterward. Do not expose ADB through router
   port forwarding or a public tunnel. A reboot test is optional; do not interrupt
   viewing just to prove a setting that can be inspected.

Use the direct ADB equivalents in `docs/CONNECT.md` on systems without a POSIX
shell. The helpers provide `capture-home`, `status`, `check`, `install [APK-path]`,
`run`, `use`, `restore`, `memory`, and `backlight-permission` (also accepted as
`brightness-permission`). `check` verifies
the saved rollback remains available without changing Home. Local rollback
records in `.device-state/` use `ro.serialno` or `ro.boot.serialno` when available
to follow the same TV across USB/Wi-Fi or address changes. If neither is
available, the helper warns and falls back to the exact ADB connection address.
For that fallback, verify the same physical TV before reusing saved state and
preserve the original Home component across address changes. `status` reports
the identity kind.

## Day/Night picture controls

Read `docs/BRIGHTNESS.md`. Version 1.0 has one sun/moon Day/Night button beside
Settings. It shows the selected BareHome preset. Short OK applies the other
preset; long OK edits the selected preset's Backlight (5–100) and Picture
Brightness (0–100), in one-point steps. Save applies and persists both levels;
Cancel/Back discards edits. There is no separate backlight selector or Restore
button.

- Require all three exact identifiers: model `CSV4K`, device `zhongshan`, product
  `KTC_MOTOROLA_4K_SA_21V2`. Only one physical KTC/Motorola TV, Android 11 / API 30,
  has been tested. Other models retain app launching without picture controls.
  Do not remove this gate to make an untested model appear supported.
- Explain that optional `WRITE_SECURE_SETTINGS` is a broad protected-settings
  permission. BareHome uses it to write only `picture_backlight` and
  `picture_brightness`. Install the APK before `./tv.sh backlight-permission`,
  which grants access for user 0. The old `WRITE_SETTINGS` grant is insufficient.
  No root or ongoing ADB connection is needed. Report a rejected grant honestly.
- Privately record both existing levels and the TV Picture mode before testing.
  Both levels must exist and be within 0–100. Preserve the user's Picture mode
  and automatic-backlight/ambient-light settings. The app refuses changes while
  automatic controls are enabled; let the user choose whether to disable them.
- Day starts from the existing valid pair, clamping its backlight minimum to 5;
  Night defaults to 5 / 20. Valid legacy Night/Restore data in the original
  Picture mode migrates its saved pair to Day and selects Night with the 5 / 20
  default. Existing customized presets are retained. Initialization, migration,
  startup, and resume must not write TV picture settings.
- Presets belong to their original TV Picture mode. The app requires returning
  to that mode before applying them and does not switch it automatically.
  Day/Night is not the TV's Movie/Standard setting. Its label shows the selected
  preset, not a measurement or guarantee that external settings still match it.
- The TV's existing Settings service applies the vendor keys to the panel. Have
  someone watch the physical image during comparisons. A successful write,
  changed number, or Android display diagnostic does not prove visible control.
  If only numbers change, open the OEM Settings once and return Home. If there
  is still no effect, report failure and use the TV's native Picture controls.
- Keep testing within the requested scope. Check short press versus long press,
  one-point adjustments and bounds, Save applying/persisting both values, and
  Cancel/Back leaving them unchanged. Preserve application data and personal app
  ordering during upgrades. Never equate a launcher process restart with a TV
  reboot, or record a check as passed merely because the code intends it.
- If a write or preference save fails, the app attempts to recover the preceding
  pair. Inspect both values and recovery before proceeding. Do not clear app
  data or discard saved presets to hide an incomplete operation.
- Finish at the user's chosen levels. Picture Brightness changes the signal and
  low levels can hide shadow detail; neither scale is measured light output.
  Explain the documented permission-revoke command. Revocation does not restore
  prior levels, and a Day preset is not a universal rollback.

### Evidence and limits

On 2026-10-17, the owner reported testing the Day/Night development build and
said it works. Separate checks of `0.3.2-modes-test` (code 7) established
installation, matching signature, default BareHome Home, unchanged 50 / 50 levels
at startup, and the simplified Day sun button beside Settings. All 35 automated
tests passed, including profile scenarios. The owner's broad confirmation is
not a separate scripted result for each editor, persistence, migration, or
recovery path. Version 1.0 packages this feature; do not imply the public
alphabetical build itself was installed for the personal test.

Earlier tests on the same TV confirmed visible backlight changes at 16/86 with
native service readbacks and restoration to 66, while Picture Brightness stayed
50. The preceding fixed Night preset was visibly confirmed comfortable at 5/20
and restored to 5/50 after a launcher process restart, with native readbacks and
a Settings/Home round trip. See `docs/BRIGHTNESS.md` for the full record.

Only Movie picture mode (9) was tested. HDR, Dolby Vision, other picture modes,
firmware, TV models, and persistence across a TV reboot remain untested. Do not
invent vendor calls, change unrelated settings, or add root to claim support.

## Recovery and boundaries

Keep the original launcher installed and enabled. Use `./tv.sh restore` to restore
the saved Home, then verify the remote's Home key. If installation or default
selection fails, diagnose the actual error and return to the working Home.
Do not factory-reset, root, unlock the bootloader, disable system packages, clear
unrelated app data, or remove the OEM launcher as part of this workflow.

Keep signing material (`.keys/`), local device state, IPs/serials, pairing codes,
and raw private diagnostics out of commits and public issues. Share only the
minimal redacted information needed for a compatibility report. Do not publish
TV screenshots containing personal app content without the user's authorization.

## When changing source

Preserve the design: one Activity, one custom View, built-in Android Java APIs,
no runtime dependencies, network, services, scheduled work, or idle redraw loop.
Use `Config.java` for simple configuration. Keep app launch and Home recovery
working even when optional backlight is unsupported.

Build with JDK 17+, SDK Platform 35, and Build Tools 35.0.0 via `./build.sh`.
Use `BAREHOME_SDK`, `ANDROID_HOME`, or `ANDROID_SDK_ROOT` for the SDK location.
The signed output is `build/BareHome.apk`; preserve the same private signing key
for compatible updates. Run relevant build and device checks, but do not install
or change a user's TV merely because you are editing documentation.

Report observed performance honestly. Process PSS is not total device memory;
an idle sample is not proof of zero CPU use forever. Historical measurements on
one Android 11 TV do not establish performance on every model or speedups inside
other apps.
