# Brightness

BareHome puts a sun, **−**, a percentage, and **+** beside Settings. Press Up from
the first app row, then Left to reach + or −. OK changes the level by five
percentage points. Holding OK does not repeatedly change it. The default range
is 5–100% so a single adjustment cannot select zero brightness.

## What it changes

BareHome uses Android's
[`Settings.System.SCREEN_BRIGHTNESS`](https://developer.android.com/reference/android/provider/Settings.System#SCREEN_BRIGHTNESS)
value, scaled between 0–255 and a percentage. An adjustment first selects manual
brightness, then writes the new value. This can replace an existing automatic
brightness setting. The percentage is the Android setting, not measured light
output or a calibrated fraction of the panel's maximum brightness.

On the tested CSV4K Android 11 TV, this changes the panel backlight and persists
while other apps run. Other TVs may use a separate manufacturer-specific picture
setting or ignore this Android value. An HDMI stick or box has no built-in panel
to control, and BareHome does not send HDMI-CEC backlight commands.

A changing number alone does **not** establish that your panel's brightness
changed. Check the visible result. If your TV ignores the value, use its own
picture settings; the launcher remains usable.

There is no dimming overlay, root access, service, timer, or polling. BareHome
reads the setting when it resumes, when Menu refreshes the screen, and after an
adjustment. An em dash means the setting is unavailable.

## Allow access once

Android requires special **Modify system settings** access for this API.
BareHome checks
[`Settings.System.canWrite`](https://developer.android.com/reference/android/provider/Settings.System#canWrite(android.content.Context))
before changing anything. The first attempt without access opens the permission
screen when the TV provides one. Allow BareHome there and return to the launcher.

Some TVs do not expose that screen. From an authorized ADB connection, use:

```sh
./tv.sh brightness-permission
```

Or directly, with the selected TV serial:

```text
adb -s TV_SERIAL shell cmd appops set --user 0 uy.nico.barehome WRITE_SETTINGS allow
```

This grants BareHome the system-settings app operation; it is not a root grant.
The app declares `android.permission.WRITE_SETTINGS` and currently uses it only
for brightness and brightness mode. This is optional: app launching does not
need the grant. Some firmware may reject it, and permission cannot make an
unsupported display respond.

To revoke the grant:

```text
adb -s TV_SERIAL shell cmd appops set --user 0 uy.nico.barehome WRITE_SETTINGS default
```

Revoking access prevents future changes; it does not restore your previous
brightness or automatic mode.

## Test without losing your original setting

Record both values before the first test:

```text
adb -s TV_SERIAL shell settings --user 0 get system screen_brightness
adb -s TV_SERIAL shell settings --user 0 get system screen_brightness_mode
```

Tap + once and verify an actual display change. Open another app and return
Home. Restore the original level afterward if this was only a test. For an exact
restore of valid original numeric values:

```text
adb -s TV_SERIAL shell settings --user 0 put system screen_brightness ORIGINAL_VALUE
adb -s TV_SERIAL shell settings --user 0 put system screen_brightness_mode ORIGINAL_MODE
```

Do not substitute `null` into these commands. If the original value was missing,
treat brightness as unsupported and leave it alone. If you previously used
automatic brightness, restore its recorded mode or turn it back on in the TV's
settings.

The step and minimum are source configuration in
[Config.java](../app/src/main/java/uy/nico/barehome/Config.java). No background
component is needed to preserve the setting; Android stores it.
