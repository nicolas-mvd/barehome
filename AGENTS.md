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
for each command. Optional backlight access is covered when the user requested
working backlight controls or used the README's installation prompt. Explain
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
6. If requested, enable and test backlight as below. Provide the installed
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

## Backlight checks

- Read `docs/BRIGHTNESS.md`. Version 0.3.1 writes the vendor's global
  `picture_backlight` setting, not Android's generic brightness setting and not
  Picture → Brightness. The TV's existing Settings service applies it to the
  panel. Do not claim universal TV support.
- Require all three exact identifiers: model `CSV4K`, device `zhongshan`, product
  `KTC_MOTOROLA_4K_SA_21V2`. Only one physical KTC/Motorola TV, Android 11 / API 30,
  has been tested. The app displays an em dash on other devices and keeps app
  launching usable. Do not remove this gate to make an untested model look
  supported.
- Explain that optional `WRITE_SECURE_SETTINGS` is a broad protected-settings
  permission, while this code writes only `picture_backlight`. Install the new
  APK before `./tv.sh backlight-permission`, which grants it to BareHome for
  user 0. The old `WRITE_SETTINGS` grant is insufficient. No root or ongoing ADB
  connection is needed. If the firmware refuses the grant, report that result.
- Read and privately record the existing global `picture_backlight` value before
  testing. If missing or outside 0–100, leave it alone. Preserve the user's
  original picture mode and automatic backlight/ambient-light settings. The app
  refuses changes while automatic backlight or ambient-light control is on;
  let the user choose whether to disable these in Picture settings.
- Have someone watch the actual panel during a low/high comparison. A successful
  write, changed number, or Android display/light-service diagnostic is not
  proof of visible panel control. If only the number changes, open the OEM
  Settings once and return Home; its service must be running. If it still has
  no visible effect, report failure and use the TV's native Picture controls.
- For historical evidence, the local `0.3.1-backlight-test` build was visibly
  verified at 16% and 86% on 2026-10-17; the native vendor service returned
  success/readbacks and the original 66% was restored. Picture → Brightness
  stayed at 50. A Settings/Home round-trip passed. Only Movie mode (9) was
  tested; HDR, Dolby Vision, other picture modes/firmware, other models, and
  reboot persistence remain untested. Do not imply the public alphabetical
  build itself was installed for that test; the test build had personal app
  ordering and the same backlight implementation.
- Restore the original backlight after diagnostics unless the user chose a new
  level. Verify the visible result and a trip away from and back to Home.
  Explain how to revoke `WRITE_SECURE_SETTINGS` using the documented command.
  Do not invent vendor service calls, change unrelated picture settings, or add
  a root dependency to make an unsupported control appear to work.

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
