"""Exercise the actual guarded picture controller with an in-memory Android provider."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    "android/Manifest.java": '''package android;
public class Manifest { public static class permission {
    public static final String WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS";
}}''',
    "android/content/Context.java": '''package android.content;
public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public abstract SharedPreferences getSharedPreferences(String name, int mode);
    public abstract ContentResolver getContentResolver();
    public abstract int checkSelfPermission(String permission);
}''',
    "android/content/ContentResolver.java": '''package android.content;
import java.util.*;
public class ContentResolver {
    public final Map<String, Integer> values = new HashMap<>();
    public final List<String> writes = new ArrayList<>();
    public final Set<Integer> failingWrites = new HashSet<>();
    public boolean denied;
}''',
    "android/content/SharedPreferences.java": '''package android.content;
public interface SharedPreferences {
    int getInt(String key, int fallback);
    Editor edit();
    interface Editor {
        Editor putInt(String key, int value);
        Editor remove(String key);
        boolean commit();
    }
}''',
    "android/content/pm/PackageManager.java": '''package android.content.pm;
public class PackageManager { public static final int PERMISSION_GRANTED = 0; }''',
    "android/os/Build.java": '''package android.os;
public class Build {
    public static String MODEL = "CSV4K";
    public static String DEVICE = "zhongshan";
    public static String PRODUCT = "KTC_MOTOROLA_4K_SA_21V2";
}''',
    "android/provider/Settings.java": '''package android.provider;
import android.content.ContentResolver;
public class Settings {
    public static class Global {
        public static int getInt(ContentResolver cr, String key, int fallback) {
            if (cr.denied) throw new SecurityException();
            Integer value = cr.values.get(key);
            return value == null ? fallback : value;
        }
        public static boolean putInt(ContentResolver cr, String key, int value) {
            if (cr.denied) throw new SecurityException();
            cr.writes.add(key + "=" + value);
            if (cr.failingWrites.contains(cr.writes.size())) return false;
            cr.values.put(key, value);
            return true;
        }
    }
}''',
    "android/widget/Toast.java": '''package android.widget;
import android.content.Context;
public class Toast {
    public static final int LENGTH_LONG = 1;
    public static String lastMessage;
    public static Toast makeText(Context context, CharSequence message, int duration) {
        lastMessage = message.toString(); return new Toast();
    }
    public void show() {}
}''',
}


class PictureControllerTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK is required")
    def test_explicit_commands_guards_and_recovery(self):
        with tempfile.TemporaryDirectory(prefix="barehome-picture-tests-") as tmp:
            root = Path(tmp)
            sources = []
            for name, contents in STUBS.items():
                source = root / name
                source.parent.mkdir(parents=True, exist_ok=True)
                source.write_text(contents)
                sources.append(str(source))
            sources += [str(ROOT / "app/src/main/java/uy/nico/barehome" / name)
                        for name in ("PictureProfiles.java", "PictureController.java", "PictureCommand.java")]
            sources.append(str(ROOT / "tests/PictureControllerTest.java"))
            subprocess.run(["javac", "--release", "8", "-d", str(root / "classes"), *sources],
                           check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", str(root / "classes"),
                                     "uy.nico.barehome.PictureControllerTest"],
                                    check=True, capture_output=True, text=True)
            self.assertIn("behavioral assertions passed", result.stdout)
            print(result.stdout.strip())


if __name__ == "__main__":
    unittest.main()
