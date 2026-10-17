# Day/Night picture controls

BareHome 1.0 puts one **☀ Day / ☾ Night** button beside Settings. It changes
both the panel's **Backlight** and **Picture → Brightness** on the supported TV.
The button shows the selected BareHome mode; a short press switches to the other
mode, and a long press opens the selected mode's settings.

## Use the controls

Press Up from the first app row to select Settings, then Left once:

| Action | Result |
| --- | --- |
| Short OK press | Switch Day/Night and apply that mode's saved levels |
| Hold OK | Edit the currently selected mode |
| Up / Down in the editor | Choose Backlight, Picture Brightness, Save, or Cancel |
| Left / Right on Backlight | Adjust 5–100 in one-point steps |
| Left / Right on Picture Brightness | Adjust 0–100 in one-point steps |
| OK on Save | Apply both levels and remember them for that mode |
| OK on Cancel, or Back | Close the editor without applying or saving edits |

Day and Night remember independent values. **Night starts at Backlight 5 /
Picture Brightness 20**. Day starts from the TV's current valid levels, with a
minimum backlight of 5. Existing customized presets survive an in-place update.
Starting or resuming BareHome does **not** apply a preset automatically.

When upgrading the earlier local Night/Restore build in its original Picture
mode, the saved pre-Night pair becomes Day, Night starts at 5 / 20, and Night is
selected. Initialization and migration do not change the TV's actual levels.

These presets belong to the TV Picture mode in which they were initialized.
If you change Movie to Standard or another Picture mode, return to the original
mode before using them. BareHome does not change the TV's Picture mode itself.
The Day/Night label identifies the selected preset; external Picture changes
can make the TV's current levels differ from it.

## What the two levels change

**Backlight** controls the panel's illumination. **Picture Brightness** changes
the picture signal; lowering it can hide shadow detail. Neither scale measures
actual emitted light. Adjust for comfort while checking that dark scenes remain
visible.

BareHome writes the manufacturer's `picture_backlight` and `picture_brightness`
keys in [`Settings.Global`](https://developer.android.com/reference/android/provider/Settings.Global).
On the tested firmware, the TV's existing Settings service observes those keys
and applies them through its native picture-control interface. BareHome writes
only when you switch modes or Save edits. It adds no service, idle polling,
dimming overlay, or continuous ADB connection. It does not write Android's
`screen_brightness` or `screen_brightness_mode`.

The OEM Settings service must be running. If the numbers change without a
visible response, open the TV's Settings, return Home, and test again. Do not
disable or force-stop that component. If there is still no visible effect, use
the TV's own Picture controls and report the limited result.

Both existing picture values must be numeric and within 0–100. BareHome refuses
changes while automatic backlight or ambient-light control is enabled. Turn
those options off yourself in Picture settings if you want manual control;
BareHome does not silently disable them. If either write or saving the preset
fails, it attempts to restore the preceding pair and reports the failure.

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

The app requires **all three model, device, and product values** to match.
This is not general Motorola, KTC, MediaTek, or Google TV support. Matching
identifiers on different firmware do not prove compatibility. On other TVs,
app launching still works, but picture controls report unavailable. There is
no generic Android brightness fallback or HDMI-CEC panel control.

HDR, Dolby Vision, other Picture modes, firmware, and TV models, and persistence
across a TV reboot have **not** been tested.

## Allow access once

Install BareHome 1.0, then confirm the TV's identity through an authorized ADB
connection. See [Connect and install](CONNECT.md) for USB and network debugging.
Replace `TV_SERIAL` with the selected device:

```text
adb -s TV_SERIAL shell getprop ro.product.model
adb -s TV_SERIAL shell getprop ro.product.device
adb -s TV_SERIAL shell getprop ro.product.name
adb -s TV_SERIAL shell am get-current-user
```

The three identifiers must match the table above. These commands and the helper
target Android user **0**. If another user is active, adapt the workflow for
that user rather than granting access to the wrong profile.

From the repository on macOS or Linux:

```sh
export BAREHOME_TV='YOUR_ADB_SERIAL_OR_IP:PORT'
./tv.sh backlight-permission
```

`./tv.sh brightness-permission` remains an alias. Direct ADB equivalent,
including Windows:

```text
adb -s TV_SERIAL shell pm grant --user 0 uy.nico.barehome android.permission.WRITE_SECURE_SETTINGS
```

This optional permission is broader than picture access: Android's
[`WRITE_SECURE_SETTINGS`](https://developer.android.com/reference/android/Manifest.permission#WRITE_SECURE_SETTINGS)
allows writing protected system settings. BareHome uses it only for
`picture_backlight` and `picture_brightness`. Grant it only if you want these
controls. It requires authorized ADB, **not root**; there is no normal in-app
permission dialog. Some firmware may reject the grant. App launching and Home
selection do not require it, and everyday picture control does not need ADB
after the permission is granted.

Upgrading from 0.3.1 normally retains this permission. The older 0.3.0
`WRITE_SETTINGS` / “Modify system settings” access is insufficient; grant
`WRITE_SECURE_SETTINGS` after updating. BareHome no longer uses the old app-op,
which you can clear separately:

```text
adb -s TV_SERIAL shell cmd appops set --user 0 uy.nico.barehome WRITE_SETTINGS default
```

To revoke picture access:

```text
adb -s TV_SERIAL shell pm revoke --user 0 uy.nico.barehome android.permission.WRITE_SECURE_SETTINGS
```

Revoking access prevents future changes; it does not restore earlier levels.
Choose the desired mode first if needed. Day applies its saved pair; it is not
a factory reset or a record of every external change. You can always use the
TV's own Picture settings.

## Test on your TV

Before testing, privately record both levels and the current Picture mode:

```text
adb -s TV_SERIAL shell settings get global picture_backlight
adb -s TV_SERIAL shell settings get global picture_brightness
adb -s TV_SERIAL shell settings get global picture_mode
```

If either level is missing or outside 0–100, stop; do not invent values. With
someone watching, compare Day/Night, edit a preset, and check that Save applies
it while Cancel/Back leaves it unchanged. Check the actual image as well as
stored values: a changed number alone is not proof of physical picture control.
A successful settings write does not prove every HDR mode or app behaves alike.

Return to the desired levels afterward. Use the TV's Picture controls for an
exact return to your recorded values; a saved Day preset may differ from them.
Check a Settings → Home round trip and a launcher process restart separately
from a TV reboot. Keep raw diagnostics and private device identifiers local.

## What was tested

On **2026-10-17**, the owner tested the new Day/Night development build on the
CSV4K above and reported that it works. That is an owner-reported functional
check, not a separate scripted result for each long/short press, Save/Cancel,
persistence, migration, or recovery path.

The local `0.3.2-modes-test` build (code 7) also passed installation, signing
certificate, and default-Home checks. Startup left the existing Backlight 50 /
Picture Brightness 50 unchanged. A screenshot confirmed the Day sun button next
to Settings without separate backlight controls. All **35 automated tests**
passed, including profile scenarios. Version 1.0 packages that feature; the
physical test used personal app ordering and the public build defaults to
alphabetical ordering.

Earlier evidence on the same TV in Movie mode:

- The `0.3.1-backlight-test` build changed Backlight from 16 to 86, with a clear
  visible difference confirmed by the person watching. The native vendor service
  returned success (0) and both values; the original 66 was restored. Picture
  Brightness stayed at 50, and a Settings → Home round trip passed.
- The fixed-preset `0.3.2-night-test` build applied Night 5 / 20 and restored
  5 / 50 after a launcher process restart. The native service accepted Picture
  Brightness writes and read back 20 and 50. Settings → Home preserved 5 / 20.
  The owner confirmed the Night result looked dimmer and comfortable.

These checks establish the observed behavior on one TV. They do not establish
support for other models, other Picture modes, HDR/Dolby Vision, or persistence
across a TV reboot.

## Earlier brightness controls

Version 0.3.1 offered separate backlight −/+ controls in five-point steps. The
1.0 Day/Night button and per-mode editor replace them.

Version 0.3.0 wrote Android's generic `Settings.System.SCREEN_BRIGHTNESS` value.
Its number changed, but the CSV4K panel did not visibly respond between 5% and
100%, and Picture Brightness stayed at 50. The earlier claim of working panel
control was retracted. Version 0.3.1 replaced that path with the visibly tested
vendor backlight integration used in 1.0.
