# BareHome

A tiny home screen for Android TV. Text, a remote, your apps.

Inspired by the suckless approach: small code, few moving parts, configuration in
source. One Activity. One Canvas View. Android's built-in Java APIs. No libraries,
Gradle, ads, recommendations, artwork, network access, services, or idle polling.

![BareHome preview](docs/barehome.svg)

*Illustration; installed apps vary.*

## Put it on my TV

Copy this into ChatGPT or a local coding agent:

> I found https://github.com/nicolas-mvd/barehome and want it on my TV. Read the
> README, AGENTS.md, docs/CONNECT.md, and docs/BRIGHTNESS.md. Help me connect my TV
> through wireless or USB debugging, install BareHome, test it, and make it my
> default launcher. Save my previous launcher so I can switch back. Enable the
> backlight buttons if my TV supports them. Use local tools if you have access
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

| Control | Action |
| --- | --- |
| D-pad / OK | Select / open an app |
| Up from the first row | Select Settings |
| Left from Settings | Reach backlight +, then − |
| OK on + or − | Change the supported TV's backlight by 5 percentage points |
| Down from the header | Return to the app grid |
| Home | Return to BareHome and the previous selection |
| Back | Stay on the home screen |
| Menu | Refresh installed apps and backlight |

Apps are alphabetical by default. The list includes TV apps and falls back to
ordinary Android launcher entries. Installing or removing an app refreshes the
list when you return home. Long labels are shortened to fit.

The sun controls now change the **actual panel backlight on the tested CSV4K
TV** using its manufacturer's setting. This fixes the ineffective Android
brightness control in 0.3.0. Other TVs display an em dash: there is no generic
brightness fallback. The launcher still works without backlight access.

After installing 0.3.1, grant the optional permission once with
`./tv.sh backlight-permission`. The old `WRITE_SETTINGS` grant is insufficient.
See [how to enable and test backlight controls](docs/BRIGHTNESS.md), including
the permission's scope and exact supported device identifiers.

## Compatibility

Early beta. Requires Android TV or Google TV with Android 8.0 / API 26 or newer.
The app has no native libraries, so it is not tied to a CPU architecture.
**One physical TV has been tested:** CSV4K, KTC/Motorola, Android 11 / API 30,
device `zhongshan`, product `KTC_MOTOROLA_4K_SA_21V2`. Backlight access requires
all three model/device/product identifiers to match. It is not enabled for
every Motorola, KTC, MediaTek, or Google TV.

On 2026-10-17, a local `0.3.1-backlight-test` build using the same backlight code
was visibly tested at **16% and 86%**, then restored to 66%. The person watching
confirmed a clear physical difference; vendor service readbacks matched.
Settings → Home round-trip also passed. This was Movie picture mode (9), with
personal app ordering; the public build defaults to alphabetical ordering.
HDR, Dolby Vision, other picture modes/firmware, and backlight persistence across
a reboot have not been tested. Other TV models have not been hardware-tested.

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
font size, columns, spacing, colors, and brightness steps. Missing pinned apps
are ignored. There is no configuration service or dependency stack to maintain.

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

BareHome has no network permission, telemetry, account, or cloud service. It
queries installed launchable apps locally and remembers the selected app. The
optional backlight feature declares `WRITE_SECURE_SETTINGS`, granted explicitly
through ADB. This is a broad system-settings permission; BareHome's backlight
code writes only the vendor's `picture_backlight` value. App launching does not
require it. [Grant and revoke instructions](docs/BRIGHTNESS.md#allow-access-once).

[MIT licensed](LICENSE). Small, focused contributions are welcome. Include your
TV model and Android version in compatibility reports; omit IP addresses, ADB
pairing codes, device identifiers, and private logs.
