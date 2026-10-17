"""Exercise device selection and rollback ordering without contacting any TV."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SOURCE = Path(__file__).resolve().parents[1]
APP = "uy.nico.barehome/.MainActivity"
PREVIOUS = "org.example.home/.HomeActivity"
RESOLVER = "android/com.android.internal.app.ResolverActivity"

MOCK_ADB = r'''#!/usr/bin/env python3
import json
import os
from pathlib import Path
import subprocess
import sys

root = Path(os.environ["MOCK_ROOT"])
state_path = root / "devices.json"
states = json.loads(state_path.read_text())
args = sys.argv[1:]
if len(args) < 3 or args[0] != "-s":
    sys.exit("Every adb call must specify -s SERIAL")
serial, command = args[1], args[2:]
device = states.get(serial)
unknown_serials = (None, "", "unknown", "UNKNOWN", "null", "NULL", "none", "None")
identity = (device or {}).get("serialno")
if identity in unknown_serials:
    identity = (device or {}).get("boot_serialno")
if identity in unknown_serials:
    identity_key = "transport-" + serial.encode().hex()
else:
    identity_key = "serial-" + identity.encode().hex()
backup = root / ".device-state" / identity_key / "previous-home"
with (root / "calls.jsonl").open("a") as log:
    log.write(json.dumps({"serial": serial, "command": command,
                          "backup": backup.read_text() if backup.exists() else None}) + "\n")
if not device or device.get("offline"):
    sys.exit("error: device offline or not found")
if command == ["get-state"]:
    print("device")
elif command == ["shell", "am", "get-current-user"]:
    print(device.get("active_user", 0))
elif command == ["shell", "getprop", "ro.serialno"]:
    print(device.get("serialno", ""))
elif command == ["shell", "getprop", "ro.boot.serialno"]:
    print(device.get("boot_serialno", ""))
elif command[:4] == ["shell", "cmd", "package", "resolve-activity"]:
    home = device["home"]
    if device.get("full_components"):
        package, activity = home.split("/", 1)
        home = package + "/" + (package + activity if activity.startswith(".") else activity)
    print(home + "\r")
elif command[:4] == ["shell", "cmd", "package", "query-activities"]:
    if device.get("query_failure"):
        sys.exit("query failed")
    print("\n".join(device["candidates"]))
elif command[0] == "install":
    if not backup.exists():
        sys.exit("BUG: installation happened before rollback was saved")
    if device.get("install_failure"):
        sys.exit("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]")
    device["home"] = "android/com.android.internal.app.ResolverActivity"
    print("Success")
elif command[:4] == ["shell", "cmd", "package", "set-home-activity"]:
    if device.get("set_failure"):
        sys.exit("HOME change rejected")
    if not device.get("set_no_effect"):
        # Like ADB, interpret the joined argument through a remote shell. This
        # catches local quoting that disappears before a nested class's $ sign.
        parsed = subprocess.run(["sh", "-c", 'printf "%s" ' + command[-1]],
                                text=True, capture_output=True, check=True)
        device["home"] = parsed.stdout
    print("Success")
elif command == ["shell", "input", "keyevent", "KEYCODE_HOME"]:
    pass
elif command[:4] == ["shell", "am", "start", "-W"]:
    print("Error: Activity class does not exist." if device.get("am_error") else "Status: ok")
elif command[:4] == ["shell", "cmd", "appops", "set"]:
    device["brightness_permission"] = "allow"
elif command[:4] == ["shell", "cmd", "appops", "get"]:
    print("WRITE_SETTINGS: " + device.get("brightness_permission", "default"))
elif command == ["shell", "dumpsys", "meminfo", "uy.nico.barehome"]:
    print("TOTAL PSS: 12345")
else:
    sys.exit("Unexpected mock adb command: " + repr(command))
state_path.write_text(json.dumps(states))
'''


class InstallerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="barehome installer ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        shutil.copy2(SOURCE / "tv.sh", self.root / "tv.sh")
        self.adb = self.root / "mock adb"
        self.adb.write_text(MOCK_ADB)
        self.adb.chmod(0o755)
        (self.root / "build").mkdir()
        self.apk = self.root / "build" / "BareHome.apk"
        self.apk.write_bytes(b"test APK")
        self.env = os.environ.copy()
        self.env.update(BAREHOME_ADB=str(self.adb), MOCK_ROOT=str(self.root))
        self.env.pop("BAREHOME_TV", None)
        self.devices = {"tv-one:5555": self.device(serialno="unit-one"),
                        "usb-two": self.device("org.other/.Launcher", serialno="unit-two")}
        self.save_devices()

    @staticmethod
    def device(home=PREVIOUS, **options):
        return {"home": home, "candidates": [home, APP], **options}

    def save_devices(self):
        (self.root / "devices.json").write_text(json.dumps(self.devices))

    def read_devices(self):
        return json.loads((self.root / "devices.json").read_text())

    def backup(self, serial="tv-one:5555"):
        device = self.read_devices().get(serial, {})
        identity = device.get("serialno")
        if identity in (None, "", "unknown", "UNKNOWN", "null", "NULL", "none", "None"):
            identity = device.get("boot_serialno")
        key = "serial-" + identity.encode().hex() if identity else "transport-" + serial.encode().hex()
        return self.root / ".device-state" / key / "previous-home"

    def calls(self):
        log = self.root / "calls.jsonl"
        return [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []

    def run_tv(self, *args, serial="tv-one:5555", success=True):
        env = self.env.copy()
        if serial is not None:
            env["BAREHOME_TV"] = serial
        result = subprocess.run(["sh", str(self.root / "tv.sh"), *args], env=env,
                                cwd=self.root, text=True, capture_output=True)
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def assert_no_home_change(self, calls=None):
        for call in self.calls() if calls is None else calls:
            self.assertNotIn("set-home-activity", call["command"])
            self.assertNotIn("KEYCODE_HOME", call["command"])

    def test_explicit_serial_required_even_with_multiple_devices(self):
        self.run_tv("install", serial=None, success=False)
        self.assertEqual(self.calls(), [])

    def test_unknown_target_never_falls_back_to_other_device(self):
        self.run_tv("install", serial="unknown", success=False)
        self.assertEqual([call["serial"] for call in self.calls()], ["unknown"])
        self.assertFalse(self.backup("unknown").exists())

    def test_install_saves_before_chooser_then_use_and_restore(self):
        self.run_tv("install")
        install = [call for call in self.calls() if call["command"][0] == "install"]
        self.assertEqual(len(install), 1)
        self.assertEqual(install[0]["backup"], PREVIOUS + "\n")
        self.assertEqual(install[0]["command"], ["install", "--no-incremental", "--user", "0", "-r", str(self.apk)])
        self.assertEqual(self.read_devices()["tv-one:5555"]["home"], RESOLVER)
        self.run_tv("use")
        self.assertEqual(self.read_devices()["tv-one:5555"]["home"], APP)
        self.run_tv("restore")
        self.assertEqual(self.read_devices()["tv-one:5555"]["home"], PREVIOUS)
        self.assertEqual(self.backup().read_text(), PREVIOUS + "\n")
        self.assertEqual(sum("KEYCODE_HOME" in call["command"] for call in self.calls()), 2)

    def test_qualified_components_compare_equal(self):
        self.devices["tv-one:5555"]["full_components"] = True
        self.save_devices()
        self.run_tv("install")
        self.run_tv("use")
        self.run_tv("restore")
        self.assertEqual(self.read_devices()["tv-one:5555"]["home"], "org.example.home/org.example.home.HomeActivity")

    def test_nested_home_component_survives_remote_shell_expansion(self):
        nested = "org.example.home/.Home$NestedActivity"
        self.devices["tv-one:5555"] = self.device(nested, serialno="unit-one")
        self.save_devices()
        self.run_tv("install")
        self.run_tv("use")
        self.run_tv("restore")
        self.assertEqual(self.read_devices()["tv-one:5555"]["home"], nested)

    def test_non_owner_active_user_stops_before_device_changes(self):
        self.devices["tv-one:5555"]["active_user"] = 10
        self.save_devices()
        result = self.run_tv("install", success=False)
        self.assertIn("user 0 only", result.stderr)
        self.assertFalse(self.backup().exists())
        self.assertEqual([call["command"] for call in self.calls()],
                         [["get-state"], ["shell", "am", "get-current-user"]])

    def test_use_and_restore_require_saved_rollback(self):
        self.run_tv("use", success=False)
        self.run_tv("restore", success=False)
        self.assert_no_home_change()

    def test_ambiguous_or_barehome_initial_home_blocks_install(self):
        for home in (RESOLVER, APP, "No activity found"):
            with self.subTest(home=home):
                self.devices["tv-one:5555"]["home"] = home
                self.save_devices()
                self.run_tv("install", success=False)
                self.assertFalse(self.backup().exists())
        self.assertFalse(any(call["command"][0] == "install" for call in self.calls()))

    def test_previous_must_be_enabled_home_candidate(self):
        self.devices["tv-one:5555"]["candidates"] = [APP]
        self.save_devices()
        self.run_tv("install", success=False)
        self.assertFalse(self.backup().exists())
        self.assertFalse(any(call["command"][0] == "install" for call in self.calls()))

    def test_backup_preserved_across_chooser_and_barehome_updates(self):
        self.run_tv("install")
        self.run_tv("capture-home")  # The default is now the resolver.
        self.run_tv("use")
        self.run_tv("capture-home")  # The default is now BareHome.
        self.run_tv("install")
        self.assertEqual(self.backup().read_text(), PREVIOUS + "\n")

    def test_devices_have_separate_backups(self):
        self.run_tv("install")
        self.run_tv("install", serial="usb-two")
        self.assertEqual(self.backup().read_text(), PREVIOUS + "\n")
        self.assertEqual(self.backup("usb-two").read_text(), "org.other/.Launcher\n")
        self.run_tv("restore", serial="usb-two")
        self.assertEqual(self.read_devices()["tv-one:5555"]["home"], RESOLVER)

    def test_same_tv_changed_endpoint_keeps_rollback(self):
        self.run_tv("install")
        self.devices = self.read_devices()
        self.devices["new-ip:39999"] = self.devices.pop("tv-one:5555")
        self.save_devices()
        self.run_tv("use", serial="new-ip:39999")
        self.run_tv("restore", serial="new-ip:39999")
        self.assertEqual(self.read_devices()["new-ip:39999"]["home"], PREVIOUS)

    def test_reused_endpoint_does_not_reuse_other_tvs_backup(self):
        self.run_tv("capture-home")
        self.devices["tv-one:5555"] = self.device(serialno="different-unit")
        self.save_devices()
        self.run_tv("use", success=False)
        self.assert_no_home_change()

    def test_boot_serial_fallback(self):
        self.devices["tv-one:5555"].update(serialno="unknown", boot_serialno="boot-unit")
        self.save_devices()
        self.run_tv("capture-home")
        expected = self.root / ".device-state" / ("serial-" + b"boot-unit".hex()) / "previous-home"
        self.assertEqual(expected.read_text(), PREVIOUS + "\n")

    def test_no_stable_serial_warns_about_transport_fallback(self):
        self.devices["tv-one:5555"].pop("serialno")
        self.save_devices()
        result = self.run_tv("capture-home")
        self.assertIn("same physical TV", result.stderr)
        self.assertEqual(self.backup().read_text(), PREVIOUS + "\n")

    def test_install_failure_preserves_backup_and_stops(self):
        self.devices["tv-one:5555"]["install_failure"] = True
        self.save_devices()
        self.run_tv("install", success=False)
        self.assertEqual(self.backup().read_text(), PREVIOUS + "\n")
        self.assert_no_home_change()
        self.assertFalse(any("uninstall" in call["command"] for call in self.calls()))

    def test_query_failure_stops_before_install(self):
        self.devices["tv-one:5555"]["query_failure"] = True
        self.save_devices()
        self.run_tv("install", success=False)
        self.assertFalse(self.backup().exists())
        self.assertFalse(any(call["command"][0] == "install" for call in self.calls()))

    def test_set_failure_does_not_send_home(self):
        self.run_tv("capture-home")
        self.devices["tv-one:5555"]["set_failure"] = True
        self.save_devices()
        self.run_tv("use", success=False)
        self.assertFalse(any("KEYCODE_HOME" in call["command"] for call in self.calls()))

    def test_silent_home_mismatch_is_failure_for_use_and_restore(self):
        self.run_tv("install")
        self.devices = self.read_devices()
        self.devices["tv-one:5555"].update(set_no_effect=True, home="org.other/.Launcher")
        self.save_devices()
        self.run_tv("use", success=False)
        self.run_tv("restore", success=False)
        self.assertFalse(any("KEYCODE_HOME" in call["command"] for call in self.calls()))
        self.assertEqual(self.backup().read_text(), PREVIOUS + "\n")

    def test_missing_apk_stops_before_capturing_home(self):
        self.run_tv("install", "missing.apk", success=False)
        self.assertFalse(self.backup().exists())

    def test_custom_apk_path_with_spaces_keeps_single_argument(self):
        custom = self.root / "release file.apk"
        custom.write_bytes(b"APK")
        self.run_tv("install", custom.name)
        install = [call for call in self.calls() if call["command"][0] == "install"][0]
        self.assertEqual(install["command"][-1], custom.name)

    def test_check_rejects_disabled_saved_launcher_without_changes(self):
        self.run_tv("capture-home")
        self.run_tv("check")
        self.devices["tv-one:5555"]["candidates"] = [APP]
        self.save_devices()
        self.run_tv("check", success=False)
        self.assert_no_home_change()

    def test_run_detects_android_error_even_if_adb_exits_zero(self):
        self.devices["tv-one:5555"]["am_error"] = True
        self.save_devices()
        self.run_tv("run", success=False)

    def test_brightness_permission_is_scoped_and_verified(self):
        self.run_tv("brightness-permission")
        self.assertEqual(self.calls()[-2]["command"], ["shell", "cmd", "appops", "set", "--user", "0", "uy.nico.barehome", "WRITE_SETTINGS", "allow"])
        self.assertEqual(self.calls()[-1]["command"], ["shell", "cmd", "appops", "get", "--user", "0", "uy.nico.barehome", "WRITE_SETTINGS"])


if __name__ == "__main__":
    unittest.main()
