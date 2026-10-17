# Connect your TV and install BareHome

You need a computer, the TV remote, and either a supported debugging connection
over your local network or a genuine USB data connection. The TV must run
Android TV / Google TV, Android 8.0 or later.

An AI assistant with local command access can carry out these steps. In an
ordinary ChatGPT web chat, run the commands yourself and share only the output
needed for the next step. Connecting ADB does not give a remote chat access to
your computer.

## 1. Get ADB and enable debugging

Install Google's [SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools).
Add its directory to `PATH`, or run commands from that directory. On Windows
PowerShell, use `./adb.exe` instead of `adb` when it is not on `PATH`.

```text
adb version
```

On the TV, open Settings → System → About and select **Android TV OS build** seven
times to enable Developer options. Menu names vary. In Developer options, look
for **Wireless debugging**, **Network debugging**, or **USB debugging**.

Use the matching connection method below. Google's
[ADB connection documentation](https://developer.android.com/tools/adb) is the
reference for pairing and USB setup.

### A. Wireless debugging with a pairing code

The standard TV pairing workflow is available on Android 13+ when the manufacturer
exposes it. Connect the computer and TV to the same network. Enable Wireless
debugging and choose **Pair device with pairing code**.

```text
adb pair TV_IP:PAIRING_PORT
```

Enter the displayed code at the terminal prompt. Keep it out of command history,
screenshots, and public logs. Then use the address on the main Wireless debugging
screen if the device has not connected automatically:

```text
adb connect TV_IP:CONNECTION_PORT
adb devices -l
```

**Pairing and connection ports can differ.** Use the current values from the TV.
The connection port may change after restarting debugging.

### B. Network debugging, including some TVs labeled “USB debugging”

Some manufacturers expose ADB over the network after enabling a debugging switch,
occasionally labeled USB debugging. This is vendor behavior, not a promise that
every USB debugging switch enables Wi-Fi access.

Use the TV's displayed address/port or the manufacturer's documented port:

```text
adb connect TV_IP:PORT
```

Port 5555 is common for this older mode, but do not assume it is available. Only
try it for the TV address you have identified. Accept **Allow USB debugging?** on
the TV when it appears, then run `adb devices -l` again.

If the connection is refused and the TV has no network debugging option, use a
supported USB connection. Enabling USB debugging alone cannot open an otherwise
unavailable network listener.

### C. USB data connection

Enable USB debugging. Connect the computer to a TV port that the manufacturer
supports for Android debugging/device mode, using a suitable data cable. Many
TV USB ports are host-only; a normal USB-A-to-USB-A cable is not a substitute for
a supported debug connection. Some sticks use their USB port only for power.

```text
adb devices -l
```

Accept the authorization prompt on the TV. Windows may require the manufacturer's
ADB USB driver. If no device appears, check the port's documentation and cable.

Optionally, once USB ADB is working, switch that selected device to network ADB:

```text
adb -s USB_SERIAL tcpip 5555
adb connect TV_IP:5555
adb devices -l
```

Use the network serial for subsequent commands. This legacy mode may need to be
enabled again after a restart.

## 2. Identify the TV before changing it

Find the intended TV in `adb devices -l`. Its state must be `device`, not
`unauthorized` or `offline`. Replace `TV_SERIAL` below with its exact serial or
`IP:port`:

```text
adb -s TV_SERIAL shell getprop ro.product.model
adb -s TV_SERIAL shell getprop ro.build.version.release
adb -s TV_SERIAL shell getprop ro.build.version.sdk
adb -s TV_SERIAL shell am get-current-user
```

Check that this is your TV, API level is at least 26, and the active Android user
is **0**. The helper and examples target user 0. If another user is active, stop
and adapt the workflow for that profile instead of changing user 0 blindly.
Always select the device explicitly, even when only one appears connected.

## 3. Save the current Home, then install

Download `BareHome.apk` from the
[latest GitHub release](https://github.com/nicolas-mvd/barehome/releases/latest),
or [build it yourself](../README.md#build-and-change-it). Verify the release
checksum when one is supplied.

### macOS / Linux helper

Clone or download this repository. In its directory:

```sh
export BAREHOME_TV='YOUR_ADB_SERIAL_OR_IP:PORT'
# Optional if adb is not on PATH:
# export BAREHOME_ADB='/path/to/platform-tools/adb'
./tv.sh status
./tv.sh capture-home
./tv.sh install /path/to/BareHome.apk
./tv.sh run
```

**Capture Home before installation.** Adding another Home app can cause Android
to clear its previous default. The helper also checks/captures it before install.
Keep the local rollback record in `.device-state/`. The helper uses the TV's
hardware serial when available, so the record follows the same TV across changes
of IP, port, or USB connection. If the TV exposes no hardware serial, it falls
back to the exact ADB connection address and warns you. In that case, preserve
your saved Home component if the address changes and verify the physical TV
before reusing a record. `./tv.sh status` reports which identity is in use.

With BareHome visible, check D-pad navigation, opening an app, and Settings.
Then make it the default:

```sh
./tv.sh use
```

Press Home on the remote and verify BareHome appears. It should remain the Home
app after restarting, subject to the TV manufacturer's behavior. A computer or
ADB connection is not needed for everyday use.

Optional Day/Night picture controls after installing 1.0, on the supported CSV4K only:

```sh
./tv.sh backlight-permission
```

Read [the Day/Night and backlight guide](BRIGHTNESS.md) for the exact supported identifiers,
permission scope, direct ADB grant/revoke commands, and a visible test. Existing
0.3.0 `WRITE_SETTINGS` access does not enable the new controls. The old helper
name `brightness-permission` remains an alias for the new grant.

### Direct ADB commands, including Windows

First resolve the current Home **before installing BareHome**:

```text
adb -s TV_SERIAL shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME
```

Save the returned `package/activity` component in a local note. A resolver or
chooser component is not a usable rollback launcher. If that appears, choose
your current launcher as the default using the TV, then resolve it again.

Install and preview:

```text
adb -s TV_SERIAL install --user 0 --no-incremental -r /path/to/BareHome.apk
adb -s TV_SERIAL shell am start --user 0 -n uy.nico.barehome/.MainActivity
```

After checking navigation and app launching:

```text
adb -s TV_SERIAL shell cmd package set-home-activity --user 0 uy.nico.barehome/.MainActivity
adb -s TV_SERIAL shell input keyevent KEYCODE_HOME
adb -s TV_SERIAL shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME
```

The final result must identify BareHome. If the manufacturer rejects this change,
try its normal Default apps/Home app setting. Do not disable or uninstall the
original launcher to force it.

## 4. Roll back or update

Restore the saved launcher using the helper:

```sh
./tv.sh restore
```

Or replace `PREVIOUS_PACKAGE/PREVIOUS_ACTIVITY` with your saved component:

```text
adb -s TV_SERIAL shell cmd package set-home-activity --user 0 PREVIOUS_PACKAGE/PREVIOUS_ACTIVITY
adb -s TV_SERIAL shell input keyevent KEYCODE_HOME
```

Verify the original Home works before optionally uninstalling BareHome through
the TV's app settings. No factory reset is needed.

For updates, install the new APK with `-r` or `./tv.sh install /path/to/BareHome.apk`.
An APK signed by a different key cannot update the installed copy. If Android
reports a signature mismatch, stop: do not automatically uninstall the working
launcher. Keep using the original release channel/signing key, or restore the
previous Home before deliberately replacing the app.

## Finish the connection

Use debugging only on a trusted local network. Do not forward ADB ports through
your router or expose them to the internet. When finished, disable wireless,
network, or USB debugging on the TV. In legacy TCP mode, `adb -s TV_SERIAL usb`
can stop the network listener on supported devices. `adb disconnect` disconnects
your computer; it does not necessarily disable the TV's listener.

BareHome and previously granted backlight access work without an ADB connection.
