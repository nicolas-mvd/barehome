# Backlight controls

BareHome 0.3.1 puts a sun, **−**, a percentage, and **+** beside Settings. On the
supported TV, these control the panel's actual **Backlight** setting. Press Up
from the first app row, then Left to reach + or −. OK changes the value by five
percentage points. Holding OK does not repeatedly change it. Adjustments are
limited to 5–100%; zero cannot be selected from the launcher.

## Which TVs have actually been tested?

**One physical TV**, with these identifiers:

| Field | Tested value |
| --- | --- |
| Model (`ro.product.model`) | `CSV4K` |
| Manufacturer / branding | KTC / Motorola |
| Device (`ro.product.device`) | `zhongshan` |
| Product (`ro.product.name`) | `KTC_MOTOROLA_4K_SA_21V2` |
| Android | 11 / API 30 |
| Picture mode | Movie (9) |

The app requires **all three model, device, and product values** above to match.
This is not general Motorola, KTC, MediaTek, or Google TV support. Matching
identifiers on different firmware do not prove compatibility. Other TVs show
an em dash, and the buttons report that backlight is unavailable. The launcher
still works normally. There is no fallback that merely changes an unrelated
Android brightness number, no dimming overlay, and no HDMI-CEC backlight control.

On **2026-10-17**, a local `0.3.1-backlight-test` build using this backlight code
changed the tested panel from 16% to 86%. The person watching confirmed a clear
visible difference. The native vendor service returned success (0) and read
back both levels. The original 66% was restored. The TV's separate
Picture → Brightness value stayed at 50, and the backlight survived a trip to
Settings and back Home. The test build used personal app ordering; the public
build uses alphabetical ordering.

Only Movie mode (9) was tested. HDR, Dolby Vision, other picture modes,
different firmware, other TV models, and persistence across a reboot have
**not** been tested. A successful settings write alone never establishes
physical panel control.

## What it changes

The app reads and writes the manufacturer's `picture_backlight` key in
[`Settings.Global`](https://developer.android.com/reference/android/provider/Settings.Global).
On the tested firmware, the TV's existing Settings service observes this value
and applies it through the native picture-control interface. BareHome does not
need to run a background service or send repeated commands. It reads the value
when it resumes, when Menu refreshes the screen, and after an adjustment.

The percentage is the vendor's 0–100 backlight scale, not measured light output.
**Picture → Brightness is a separate control.** BareHome leaves it unchanged and
does not write Android's `screen_brightness` or `screen_brightness_mode`.

The OEM Settings service must be running for this integration to apply changes.
If the number changes without a visible response, open the TV's Settings, return
Home, and test again. Do not disable or force-stop that system component. If it
still does not respond, use the TV's own Picture controls and report the limited
result rather than treating the number as proof.

BareHome refuses to adjust the backlight when the firmware reports automatic
backlight or ambient-light-sensor control enabled. If you want manual control,
turn those options off yourself in the TV's Picture settings. BareHome does not
silently disable them or change picture mode.

## Allow access once

Install 0.3.1 first. Confirm the TV's identity through an authorized ADB
connection, replacing `TV_SERIAL` with the selected device:

```text
adb -s TV_SERIAL shell getprop ro.product.model
adb -s TV_SERIAL shell getprop ro.product.device
adb -s TV_SERIAL shell getprop ro.product.name
adb -s TV_SERIAL shell am get-current-user
```

The three identifiers must match the table above. These commands and the helper
target Android user **0**. If another user is active, stop and adapt the workflow
for that user rather than granting access to the wrong profile.

From the repository on macOS or Linux:

```sh
export BAREHOME_TV='YOUR_ADB_SERIAL_OR_IP:PORT'
./tv.sh backlight-permission
```

The old command name, `./tv.sh brightness-permission`, remains an alias. Both now
use this grant; they do not grant the old Android brightness app operation.

Direct ADB equivalent, including Windows:

```text
adb -s TV_SERIAL shell pm grant --user 0 uy.nico.barehome android.permission.WRITE_SECURE_SETTINGS
```

This optional permission is broader than backlight access: Android's
[`WRITE_SECURE_SETTINGS`](https://developer.android.com/reference/android/Manifest.permission#WRITE_SECURE_SETTINGS)
allows writing protected system settings. BareHome's backlight code uses it only
to write `picture_backlight`. Grant it only if you want this feature. The grant
requires authorized ADB, **not root**; there is no normal in-app permission dialog
for it. Some firmware may reject the grant. App launching and Home selection do
not require it, and an ADB connection is not needed for everyday backlight use
after permission has been granted.

**Upgrading from 0.3.0:** the earlier `WRITE_SETTINGS` / “Modify system settings”
access is insufficient. Install 0.3.1 and grant `WRITE_SECURE_SETTINGS` as above.
The app no longer uses `WRITE_SETTINGS`; you can clear its old app-op separately:

```text
adb -s TV_SERIAL shell cmd appops set --user 0 uy.nico.barehome WRITE_SETTINGS default
```

To revoke the new backlight permission:

```text
adb -s TV_SERIAL shell pm revoke --user 0 uy.nico.barehome android.permission.WRITE_SECURE_SETTINGS
```

Revoking access prevents future changes; it does not restore an earlier backlight
level. You can still adjust Backlight through the TV's own Picture settings.

## Test and restore your original level

Before testing, note the backlight shown in the TV's Picture settings and read
its stored value:

```text
adb -s TV_SERIAL shell settings get global picture_backlight
```

If the value is missing or outside 0–100, stop; do not invent a default. Keep the
original value privately. With someone watching the screen, use the launcher's
buttons to choose a comfortable low and high level, and confirm the actual
picture gets dimmer and brighter. A changing percentage, stored value, or Android
display diagnostic alone is insufficient. Open Settings and return Home to
check that the chosen level remains applied.

Restore the original level afterward unless you want to keep a new one. Use the
TV's Backlight slider for an exact restore, or on the supported TV only:

```text
adb -s TV_SERIAL shell settings put global picture_backlight ORIGINAL_VALUE
```

Replace `ORIGINAL_VALUE` with the recorded integer, not `null`. The OEM Settings
service must be running for the write to reach the panel. Confirm the restored
picture visually. Do not change Picture → Brightness to compensate.

The step and minimum are source configuration in
[Config.java](../app/src/main/java/uy/nico/barehome/Config.java). No BareHome
background component is needed. The vendor stores the value; reboot persistence
has not yet been verified.

## What was wrong in 0.3.0?

The earlier control wrote Android's generic `Settings.System.SCREEN_BRIGHTNESS`
value. That number changed, but the tested CSV4K's panel did not visibly respond
between 5% and 100%, and its Picture → Brightness stayed at 50. The earlier claim
of working panel control was retracted. Version 0.3.1 replaces that path with the
specific, visibly tested backlight integration described above.
