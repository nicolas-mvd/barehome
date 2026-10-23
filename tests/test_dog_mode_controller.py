"""Check the actual Android adapter keeps a verified, narrow energy allowlist."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    "android/Manifest.java": '''package android;
public class Manifest { public static class permission {
public static final String WRITE_SECURE_SETTINGS="android.permission.WRITE_SECURE_SETTINGS"; }}''',
    "android/content/Context.java": '''package android.content;
public abstract class Context {
 public final ContentResolver resolver = new ContentResolver(); public int homes;
 public Context getApplicationContext(){return this;}
 public ContentResolver getContentResolver(){return resolver;}
 public abstract int checkSelfPermission(String permission);
 public abstract void startActivity(Intent intent);
}''',
    "android/content/ContentResolver.java": '''package android.content;
import java.util.*;
public class ContentResolver {
 public final Map<String,String> secure=new HashMap<>(), global=new HashMap<>(), system=new HashMap<>();
 public final List<String> events=new ArrayList<>(); public final Set<String> failures=new HashSet<>();
 public boolean masterRestoreResetsAdb, adbRestoreChangesMaster;
 public int writes;
}''',
    "android/content/Intent.java": '''package android.content;
public class Intent {
 public static final int FLAG_ACTIVITY_NEW_TASK=1, FLAG_ACTIVITY_CLEAR_TOP=2;
 public final Class<?> target;
 public Intent(Context context,Class<?> target){this.target=target;}
 public Intent addFlags(int flags){return this;}
}''',
    "android/content/pm/PackageManager.java": '''package android.content.pm;
public class PackageManager { public static final int PERMISSION_GRANTED=0; }''',
    "android/os/Build.java": '''package android.os;
public class Build {public static String MODEL, DEVICE, PRODUCT;
 public static class VERSION {public static int SDK_INT;}}''',
    "android/provider/Settings.java": '''package android.provider;
import android.content.ContentResolver;
import java.util.Map;
public class Settings {
 private static boolean put(ContentResolver cr,Map<String,String> map,String key,String value){
  cr.writes++; String event=(map==cr.secure?"secure:":"global:")+key; cr.events.add(event+"="+value);
  if(cr.failures.contains(event))return false;
  if(value==null)map.remove(key);else map.put(key,value);
  if(cr.masterRestoreResetsAdb&&key.equals("development_settings_enabled")&&"1".equals(value))cr.global.put("adb_enabled","0");
  if(cr.adbRestoreChangesMaster&&key.equals("adb_enabled")&&"1".equals(value))cr.global.put("development_settings_enabled","0");
  return true;}
 public static class Secure {
  public static String getString(ContentResolver cr,String key){return cr.secure.get(key);}
  public static boolean putString(ContentResolver cr,String key,String value){return put(cr,cr.secure,key,value);}}
 public static class Global {
  public static String getString(ContentResolver cr,String key){return cr.global.get(key);}
  public static boolean putString(ContentResolver cr,String key,String value){return put(cr,cr.global,key,value);}}
}''',
    "uy/nico/barehome/MainActivity.java": '''package uy.nico.barehome; public class MainActivity {}''',
    "uy/nico/barehome/NsNowDogService.java": '''package uy.nico.barehome;
import android.content.Context;
final class NsNowDogService {static boolean available(Context context){return false;}}''',
    "uy/nico/barehome/DogModeStore.java": '''package uy.nico.barehome;
import android.content.Context;
import java.util.*;
final class DogModeStore implements DogModeTransaction.Store {
 static final Map<Context,String> records=new HashMap<>(); private final Context context;
 DogModeStore(Context context){this.context=context;}
 public String read(){return records.containsKey(context)?records.get(context):"";}
 public boolean save(String value){records.put(context,value);return true;}
}''',
}


class DogModeControllerTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK is required")
    def test_guarded_energy_adapter(self):
        with tempfile.TemporaryDirectory(prefix="barehome-dog-controller-tests-") as tmp:
            folder = Path(tmp)
            sources = []
            for name, content in STUBS.items():
                target = folder / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(content)
                sources.append(target)
            sources += [ROOT / "app/src/main/java/uy/nico/barehome" / name for name in
                        ("DogModeTransaction.java", "DogModeController.java")]
            sources.append(ROOT / "tests/DogModeControllerTest.java")
            subprocess.run(["javac", "--release", "8", "-d", str(folder / "classes"), *map(str, sources)],
                           check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", str(folder / "classes"), "uy.nico.barehome.DogModeControllerTest"],
                                    check=True, capture_output=True, text=True)
            self.assertIn("behavioral assertions passed", result.stdout)
            print(result.stdout.strip())


if __name__ == "__main__":
    unittest.main()
