# BareHome

A tiny home screen for Android TV. Text, a remote, your apps.

**1.4:** adds optional Dog Mode for BareRemote: NS NOW / CANAL 10, temporary TV
energy overrides and exact restoration on exit. See [one-time setup and recovery](docs/DOG_MODE.md).

Inspired by the suckless approach: small code, few moving parts, configuration in
source. One launcher Activity and Canvas View, plus small update and consent screens. Android's built-in Java APIs. No libraries,
Gradle, ads, recommendations, artwork or idle polling. Dog Mode's explicitly
enabled Accessibility helper runs only for its NS NOW session. The optional
phone mode check sends one authenticated local-network reply per request. Update
checks make one bounded GitHub request when due; there is no idle updater service.

![BareHome preview](docs/barehome.svg)

*Illustration; installed apps vary.*

## Put it on my TV

Copy this into ChatGPT or a local coding agent:

> I found https://github.com/nicolas-mvd/barehome and want it on my TV. Read the
> README, AGENTS.md, docs/CONNECT.md, and docs/BRIGHTNESS.md. Help me connect my TV
> through wireless or USB debugging, install BareHome, test it, and make it my
> default launcher. Save my previous launcher so I can switch back. Enable the
> Day/Night picture controls if my TV supports them. Use local tools if you have access
> to my computer; otherwise guide me through the commands.

The connection runs through **ADB on your computer**. A normal ChatGPT web chat
can guide you, but cannot reach your home network by itself. A local agent such
as [Codex CLI](https://learn.chatgpt.com/docs/codex/cli) can run the commands when
given access to your computer. BareHome itself has no AI or account requirement.

[Download the latest APK](https://github.com/nicolas-mvd/barehome/releases/latest)
and follow [Connect and install](docs/CONNECT.md). AI assistants should start with
[AGENTS.md](AGENTS.md).

Already connected through ADB? From this repository on macOS or Linux:

```sh
export BAREHOME_TV='YOUR_ADB_SERIAL_OR_IP:PORT'
./tv.sh status
./tv.sh capture-home                         # Before installing a new Home app
./tv.sh install /path/to/BareHome.apk
./tv.sh run                                 # Check the screen and remote
./tv.sh use                                 # Make BareHome the default Home
./tv.sh backlight-permission                # Optional; supported TV only
```

To switch back: `./tv.sh restore`. Keep the original launcher installed.
The [full guide](docs/CONNECT.md) also has direct ADB commands for Windows.

## Use

**BareHome 1.4** has one **☀ Day / ☾ Night** button beside Settings. Click to
switch modes; hold OK to customize the selected mode.

| Control | Action |
| --- | --- |
| D-pad / OK | Select / open an app |
| Up from the first row | Select Settings |
| Left from Settings | Select Day/Night |
| Short OK on Day/Night | Apply the other mode's saved levels |
| Hold OK on Day/Night | Edit the selected mode's Backlight and Picture Brightness |
| Up / Down in the editor | Choose a setting, Save, or Cancel |
| Left / Right in the editor | Adjust the selected level by one point |
| Save / Cancel or Back | Apply and remember edits / discard edits |
| Down from the header | Return to the app grid |
| Home | Return to BareHome and the previous selection |
| Back on the home screen | Stay on the home screen |
| Menu | Refresh installed apps and picture-mode state |

Day and Night each remember **Backlight (5–100)** and **Picture Brightness
(0–100)**. Night starts at **5 / 20**; Day starts from the TV's existing levels.
The button shows the selected mode. Starting BareHome does not reapply a preset,
and picture controls use no background service or polling. The [picture-controls guide](docs/BRIGHTNESS.md)
explains defaults, upgrades, and the difference between the two settings.

These controls use the manufacturer's actual panel-backlight and picture-signal
settings on the tested CSV4K. After installing, enable the optional permission
once with `./tv.sh backlight-permission`. The old `WRITE_SETTINGS` grant is
insufficient. Other TVs retain normal app launching, with picture controls
unavailable. There is no generic Android brightness fallback.

Version **1.2** lets [BareRemote](https://github.com/nicolas-mvd/bareremote)
check the selected preset and actual picture levels, then display one sun/moon
button. Tap that phone button to toggle using the TV's saved choice. Both phones
can request the current TV state without guessing from their own previous tap.
An idle phone widget retains its last confirmed state; Sync refreshes it.

The request arrives through the already paired Android TV Remote connection.
A tiny windowless entry point passes it to a short receiver, which sends one
authenticated UDP reply to the requesting phone on the local network. This
requires Android's `INTERNET` permission, but adds no TV network listener,
permanent service, cloud connection or polling. The same model, picture-mode,
automatic-lighting and permission checks apply. A query never writes TV picture
settings. Older explicit Day/Night links remain compatible.

Upgrade in place to retain profiles, permission and launcher data. See
[remote picture controls](docs/BRIGHTNESS.md#from-a-paired-phone) for the reply
protocol, network requirements and limits. A settings confirmation is not a
measurement of light output or a guarantee of uninterrupted playback.

BareRemote's source button uses `barehome://inputs` to open the TV's existing
input menu. BareHome dispatches `com.android.tv.action.VIEW_INPUTS` rather than
selecting an HDMI port automatically. The TV must provide an exported handler for
that action; otherwise a message reports that the source menu is unavailable.
Source selection does not need the optional picture permission or CSV4K model
match. This bridge requires BareHome 1.1 or newer installed on the TV. On the tested
CSV4K, both the BareRemote app and widget opened the native Inputs list, including
PlayStation 5; no port was selected during verification.

Apps are alphabetical by default. Version 1.3 saves the initial compiled pinned
order locally so subsequent public APK updates preserve a personal build's order. The list includes TV apps and falls back to
ordinary Android launcher entries. Installing or removing an app refreshes the
list when you return home. Long labels are shortened to fit.

## Dog Mode

BareRemote Android 0.7.0 or macOS 0.4.0 can request **NS NOW → CANAL 10** and keep
the supported TV on while you are away. BareHome saves the exact existing sleep,
inactivity and no-signal power-off values before applying temporary overrides.
Ending Dog Mode restores those values and returns Home. A durable recovery
record remains until restoration succeeds; another paired remote can end it.

One-time consent enables the NS NOW Accessibility helper only during Dog Mode.
It selects CANAL 10 through NS NOW's own interface and responds to that app's
three-hour continuation prompt. It is disabled on exit. Android's permission
has broader screen-reading/control scope; the implementation checks the package,
window and fixed controls before acting and does not record screen content.
NS NOW requires USB debugging to be off.

This integration is limited to the exact CSV4K / Android 11 configuration and
NS NOW 1.1.266. Changed app layouts, login dialogs or unavailable channels can
prevent startup. A channel-label confirmation is not proof of decoded video or
uninterrupted playback. See [setup, permissions, recovery and test limits](docs/DOG_MODE.md).

## Updates

A small download-arrow button appears to the left of Day/Night when a newer
release is available. Select it to download, verify and open Android's install
confirmation. Hold OK on **Settings** for an immediate check; a short press still
opens the TV settings. The automatic check happens only on returning Home and at
most once every 24 hours. It does not wake the TV or download an APK in the background.

Android may ask you to allow BareHome to install unknown apps. Updates preserve
presets and locally saved app order. Custom builds must first run an updater-enabled
build with their existing pinned packages before switching to public APKs.
The first updater-enabled APK needs an ordinary installation; future updates can
use the launcher. See [update checks and publishing](docs/UPDATES.md).

## Compatibility

Requires Android TV or Google TV with Android 8.0 / API 26 or newer.
The app has no native libraries, so it is not tied to a CPU architecture.
**One physical TV has been tested:** CSV4K, KTC/Motorola, Android 11 / API 30,
device `zhongshan`, product `KTC_MOTOROLA_4K_SA_21V2`. Backlight access requires
all three model/device/product identifiers to match. It is not enabled for
every Motorola, KTC, MediaTek, or Google TV.

On the repeated 2026-10-17 phone-remote comparison, the owner confirmed that the
Night 5 / 20 to Day 60 / 50 change was clearly visible. The request came from the
signed BareRemote 0.2.0 APK on the Android 15 emulator. This confirms the visible
preset change on the tested panel; it does not establish uninterrupted playback
in every TV app.

The **1.2 prerelease** passes all **37 host tests**, including **113 controller**
and **97 callback-format/subnet/replay assertions**. Its signed APK's actual
receiver returned an authenticated `unavailable` response on an unsupported
Android 15 emulator. The later `1.2-personal` installation on the physical CSV4K retained the default
Home, permissions and picture levels. Two authenticated state queries returned Day
without changing levels. The owner then confirmed that BareRemote Sync showed the
sun and tapping switched the TV to Night with the moon icon.

Version **1.1** remote controls were checked on this TV using the signed
BareRemote **0.2** APK's app and widget. Day/Night reached settings readbacks
of **60 / 50** and **5 / 20**, respectively, with the same SmartTube playback
activity resumed. Both source buttons opened the native Inputs menu. The TV ran
`1.1-personal` (code 9), preserving its private app order; the published `1.1`
APK uses the same control code with alphabetical ordering. These checks do not
confirm uninterrupted playback to a viewer or compatibility with other TVs.

On **2026-10-17**, the owner tested the Day/Night development build on that TV
and reported that it works. Installation, matching signing certificate, default
Home, unchanged picture levels at startup, and the simplified header were also
checked. All **35 automated tests** passed, including preset scenarios.

Earlier tests on the same TV visibly confirmed backlight changes at 16% and
86%, and the fixed Night preset at Backlight 5 / Picture Brightness 20. Those
tests included native-service readbacks. Test builds used personal app ordering;
the public build uses alphabetical ordering. Only **Movie picture mode (9)**
was tested. HDR, Dolby Vision, other picture modes/firmware, other TV models,
and persistence across a TV reboot remain untested. The owner's confirmation
is not a separate scripted verification of every editor or recovery path.
See [the full test record](docs/BRIGHTNESS.md#what-was-tested).

Some manufacturers restrict default Home selection. An HDMI streaming stick
cannot use this integration to control the connected TV's panel. Neither Home
selection nor backlight access requires root. If a TV rejects the supported
method, keep its existing launcher and report the model and Android version.

Accessibility announces the selected item and supports previous/next actions;
the grid does not yet expose a full set of individual accessibility nodes.

## Build and change it

Requires JDK 17+, Android SDK Platform 35, Build Tools 35.0.0, a POSIX shell,
`zip`, and `openssl` (for the first signing-key setup).
ADB is needed only for deployment. Set `BAREHOME_SDK` to the SDK directory, or use
`ANDROID_HOME` / `ANDROID_SDK_ROOT`.

```sh
export BAREHOME_SDK='/path/to/android-sdk'
sdkmanager --sdk_root="$BAREHOME_SDK" 'platforms;android-35' 'build-tools;35.0.0'
./build.sh
```

`sdkmanager` comes with Android's SDK Command-line Tools; install the packages
into the same SDK directory selected above.

The signed APK is `build/BareHome.apk`. The build uses `aapt2`, `javac`, `d8`,
`zipalign`, and `apksigner` directly. A first local build creates a signing key in
`.keys/`; keep it private and backed up to sign future updates. A self-built APK
normally has a different signature from the published APK and cannot replace it
as an in-place update.

Edit [Config.java](app/src/main/java/uy/nico/barehome/Config.java) for app order,
font size, columns, spacing, and colors. App order is captured on the first run;
a saved order takes precedence over later compiled defaults. Missing pinned apps
are ignored. There is no configuration service or dependency stack to maintain.

Version **1.3** completed its own GitHub-download and Android-installer update on
that physical CSV4K. The public APK retained the personal bootstrap's saved app
order, default Home, picture permission and starting 60 / 50 levels. The update
arrow disappeared afterward; holding Settings confirmed up to date. All **39 host
tests** pass, including **80 updater assertions** and seven app-order scenarios.
See [updater validation and the one-time permission flow](docs/UPDATES.md).

## Small by measurement

The original brightness build, 0.2.0, was approximately **33 KB** and measured
**12.6 MiB median resident process PSS** on the tested TV. FLauncher measured
approximately **88 MiB** in the same device assessment. A ten-second idle check
observed zero process CPU ticks. These are observations from one TV and earlier
builds, not universal performance guarantees for every release.

Measurements used settled `dumpsys meminfo` samples. On that Android build,
resident PSS was calculated as `TOTAL PSS - TOTAL SWAP PSS`. These figures describe
process memory, not the total device memory saved; graphics buffers can be
accounted elsewhere. Speed inside other apps was not established by this test.

## Privacy and license

BareHome has no telemetry or account. GitHub hosts update metadata and APKs. Its `INTERNET` permission
is used for bounded authenticated picture and Dog Mode replies on the local network
and bounded GitHub update checks/downloads; there is no network listener or idle polling. It queries installed launchable
apps locally and remembers the selected app. The
optional controls declare `WRITE_SECURE_SETTINGS`, granted explicitly
through ADB. This is a broad system-settings permission. Picture controls write
the vendor's `picture_backlight` and `picture_brightness` values. Consented Dog
Mode additionally writes only the three documented energy settings and adds or
removes its own Accessibility component, preserving other enabled services.
Each mode's levels and original TV Picture mode are stored locally. App launching
does not require this permission.
The exported command entry point can also be invoked by other apps on the TV;
it accepts only the saved preset choices, source menu, Dog Mode setup and
strictly validated picture/Dog Mode requests with a local reply address. It never accepts arbitrary
picture settings, levels, or activity targets.
[Grant and revoke instructions](docs/BRIGHTNESS.md#allow-access-once).

[MIT licensed](LICENSE). Small, focused contributions are welcome. Include your
TV model and Android version in compatibility reports; omit IP addresses, ADB
pairing codes, hardware serial numbers, and private logs.


Version **1.4** passes all **45 host tests**, including 164 NS NOW selector
assertions and guarded transaction/energy-recovery coverage. A short physical
CSV4K check with BareRemote macOS 0.4.0 and NS NOW 1.1.266 confirmed CANAL 10
playback, End restoring energy settings/removing the helper, and returning Home.
The three-hour prompt and long-duration playback have not been physically tested.
See the [Dog Mode validation record](docs/DOG_MODE.md#physical-validation--2026-10-23).
