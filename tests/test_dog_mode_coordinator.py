"""Run the actual asynchronous coordinator with explicit service/event test doubles."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from tests.test_dog_mode_controller import STUBS as CONTROLLER_STUBS

ROOT = Path(__file__).resolve().parents[1]
STUBS = dict(CONTROLLER_STUBS)
STUBS["android/content/Context.java"] = '''package android.content;
public abstract class Context {
 public static final int MODE_PRIVATE=0;
 public final ContentResolver resolver=new ContentResolver(); public int homes;
 public Context getApplicationContext(){return this;}
 public ContentResolver getContentResolver(){return resolver;}
 public abstract int checkSelfPermission(String value);
 public abstract void startActivity(Intent intent);
 public abstract SharedPreferences getSharedPreferences(String name,int mode);
}'''
STUBS["android/content/SharedPreferences.java"] = '''package android.content;
public interface SharedPreferences {
 String getString(String key,String fallback); Editor edit();
 interface Editor {Editor putString(String key,String value);boolean commit();}
}'''
STUBS["android/content/ContentResolver.java"] = '''package android.content;
import java.util.*;
public class ContentResolver {
 public final Map<String,String> secure=new HashMap<>(),global=new HashMap<>(),system=new HashMap<>();
 public int writes;public boolean expireOnRead;
}'''
STUBS["android/provider/Settings.java"] = '''package android.provider;
import android.content.ContentResolver; import java.util.Map;
public class Settings {
 static boolean put(ContentResolver cr,Map<String,String> m,String k,String v){cr.writes++;if(v==null)m.remove(k);else m.put(k,v);return true;}
 public static class Secure {public static String getString(ContentResolver c,String k){return c.secure.get(k);}
 public static boolean putString(ContentResolver c,String k,String v){return put(c,c.secure,k,v);}}
 public static class Global {public static String getString(ContentResolver c,String k){
 if(c.expireOnRead){c.expireOnRead=false;android.os.SystemClock.now=8000;}return c.global.get(k);}
 public static boolean putString(ContentResolver c,String k,String v){return put(c,c.global,k,v);}}
}'''
STUBS["android/os/SystemClock.java"] = '''package android.os;
public class SystemClock {public static volatile long now;public static long elapsedRealtime(){return now;}}'''
STUBS["android/os/Looper.java"] = '''package android.os;
public class Looper {public static Looper getMainLooper(){return new Looper();}}'''
STUBS["android/os/Handler.java"] = '''package android.os;
import java.util.*;
public class Handler {
 private static final List<Runnable> delayed=new ArrayList<>();
 public Handler(Looper loop){} public boolean post(Runnable r){r.run();return true;}
 public boolean postDelayed(Runnable r,long delay){synchronized(delayed){delayed.add(r);}return true;}
 public void removeCallbacks(Runnable r){synchronized(delayed){delayed.remove(r);}}
 public static void clear(){synchronized(delayed){delayed.clear();}}
 public static void runDelayed(){List<Runnable> rs;synchronized(delayed){rs=new ArrayList<>(delayed);delayed.clear();}for(Runnable r:rs)r.run();}
}'''
STUBS["uy/nico/barehome/DogModeAccessibility.java"] = '''package uy.nico.barehome;
import android.content.Context;
final class DogModeAccessibility {
 static boolean consent=true,commit=true;
 static boolean hasConsent(Context c){return consent;}
 static boolean setConsent(Context c,boolean value){consent=value;return commit;}
}'''
STUBS["uy/nico/barehome/NsNowDogService.java"] = '''package uy.nico.barehome;
import android.content.Context;
final class NsNowDogService {
 interface Completion{void complete(boolean verified);}
 static boolean schedule,enabled,monitoring,disableFailure;static int begins;static String generation,failureCode;static Completion callback;
 static void reset(){schedule=true;enabled=false;monitoring=false;disableFailure=false;begins=0;generation=null;callback=null;failureCode="";}
 static boolean available(Context c){return DogModeAccessibility.consent;}
 static boolean isEnabled(Context c){return enabled;}
 static boolean isMonitoring(String g){return monitoring&&g!=null&&g.equals(generation);}
 static boolean begin(Context c,String g,Completion cb){begins++;if(!schedule)return false;enabled=true;generation=g;callback=cb;return true;}
 static boolean activate(Context c,String g){if(!enabled||!g.equals(generation))return false;monitoring=true;return true;}
 static void cancel(Context c,String g){if(g==null||g.equals(generation)){monitoring=false;callback=null;generation=null;}}
 static boolean disable(Context c){cancel(c,null);if(disableFailure)return false;enabled=false;return true;}
 static String startupFailure(Context c,String g){return failureCode;}
}'''


class DogModeCoordinatorTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK is required")
    def test_async_start_stop_and_cleanup(self):
        with tempfile.TemporaryDirectory(prefix="barehome-dog-coordinator-tests-") as tmp:
            folder=Path(tmp)
            sources=[]
            for name,content in STUBS.items():
                path=folder/name
                path.parent.mkdir(parents=True,exist_ok=True)
                path.write_text(content)
                sources.append(path)
            sources += [ROOT/"app/src/main/java/uy/nico/barehome"/name for name in
                        ("DogModeTransaction.java","DogModeController.java","DogModeCoordinator.java",
                         "DogModeWire.java","PictureStateWire.java","PictureRequestCache.java")]
            sources.append(ROOT/"tests/DogModeCoordinatorTest.java")
            subprocess.run(["javac","--release","8","-d",str(folder/"classes"),*map(str,sources)],check=True,capture_output=True,text=True)
            result=subprocess.run(["java","-cp",str(folder/"classes"),"uy.nico.barehome.DogModeCoordinatorTest"],check=True,capture_output=True,text=True,timeout=20)
            self.assertIn("behavioral assertions passed",result.stdout)
            print(result.stdout.strip())


if __name__ == "__main__":
    unittest.main()
