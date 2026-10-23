"""Durable consent failure and component preservation, using file-backed platform doubles."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    "android/Manifest.java": '''package android; public final class Manifest { public static final class permission { public static final String WRITE_SECURE_SETTINGS="write_secure_settings"; } }''',
    "android/content/Context.java": '''package android.content; import java.io.File; import android.content.pm.PackageManager; public class Context { private final File files; private final ContentResolver resolver=new ContentResolver(); public Context(File files){this.files=files; files.mkdirs();} public File getFilesDir(){return files;} public int checkSelfPermission(String permission){return 0;} public PackageManager getPackageManager(){return new PackageManager();} public ContentResolver getContentResolver(){return resolver;} }''',
    "android/content/ContentResolver.java": '''package android.content; public final class ContentResolver { public String enabled; }''',
    "android/content/ComponentName.java": '''package android.content; public final class ComponentName { private final String value; public ComponentName(Context c,Class<?> type){value="uy.nico.barehome/"+type.getName();} private ComponentName(String v){value=v;} public static ComponentName unflattenFromString(String value){int slash=value.indexOf('/'); if(slash<=0||slash==value.length()-1)return null; String pkg=value.substring(0,slash),name=value.substring(slash+1); return new ComponentName(pkg+"/"+(name.startsWith(".")?pkg+name:name));} public String flattenToString(){return value;} @Override public boolean equals(Object other){return other instanceof ComponentName&&value.equals(((ComponentName)other).value);} @Override public int hashCode(){return value.hashCode();} }''',
    "android/content/pm/PackageManager.java": '''package android.content.pm; public class PackageManager { public static final int PERMISSION_GRANTED=0; public static class NameNotFoundException extends Exception {} public PackageInfo getPackageInfo(String name,int flags) throws NameNotFoundException{return new PackageInfo();} }''',
    "android/content/pm/PackageInfo.java": '''package android.content.pm; public class PackageInfo { public int versionCode=409; public String versionName="1.1.266"; public ApplicationInfo applicationInfo=new ApplicationInfo(); }''',
    "android/content/pm/ApplicationInfo.java": '''package android.content.pm; public class ApplicationInfo { public boolean enabled=true; }''',
    "android/os/Build.java": '''package android.os; public class Build { public static String MODEL="CSV4K",DEVICE="zhongshan",PRODUCT="KTC_MOTOROLA_4K_SA_21V2"; public static class VERSION { public static int SDK_INT=30; } }''',
    "android/provider/Settings.java": '''package android.provider; import android.content.ContentResolver; public class Settings { public static class Secure { public static final String ENABLED_ACCESSIBILITY_SERVICES="enabled_accessibility_services"; public static String getString(ContentResolver r,String key){return r.enabled;} public static boolean putString(ContentResolver r,String key,String value){r.enabled=value;return true;} } }''',
    "android/util/AtomicFile.java": '''package android.util; import java.io.*; import java.nio.file.*; public class AtomicFile { public static boolean failFinish; private final File base,temp; public AtomicFile(File file){base=file;temp=new File(file.getPath()+".new");} public FileInputStream openRead() throws FileNotFoundException{return new FileInputStream(base);} public FileOutputStream startWrite() throws IOException{return new FileOutputStream(temp);} public void finishWrite(FileOutputStream output){try {output.close();if(failFinish){temp.delete();return;}Files.move(temp.toPath(),base.toPath(),StandardCopyOption.REPLACE_EXISTING);}catch(IOException ignored){}} public void failWrite(FileOutputStream output){try{output.close();}catch(IOException ignored){}temp.delete();} }''',
    "uy/nico/barehome/NsNowDogService.java": '''package uy.nico.barehome; public class NsNowDogService {}''',
}

class DogModeAccessibilityTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK is required")
    def test_consent_failures_and_component_preservation(self):
        with tempfile.TemporaryDirectory(prefix="barehome-accessibility-tests-") as temporary:
            build = Path(temporary)
            sources = []
            for name, source in STUBS.items():
                path = build / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source)
                sources.append(path)
            sources += [ROOT / "app/src/main/java/uy/nico/barehome/DogModeAccessibility.java",
                        ROOT / "app/src/main/java/uy/nico/barehome/NsNowDogSelector.java",
                        ROOT / "tests/DogModeAccessibilityTest.java"]
            subprocess.run(["javac", "--release", "8", "-d", str(build), *map(str, sources)],
                           check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", str(build), "uy.nico.barehome.DogModeAccessibilityTest", str(build / "private")],
                                    check=True, capture_output=True, text=True)
            self.assertIn("behavioral assertions passed", result.stdout)
            print(result.stdout.strip())

    def test_system_binding_and_capability_scope(self):
        android = "{http://schemas.android.com/apk/res/android}"
        manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
        service = next(item for item in manifest.findall("application/service") if item.get(android + "name") == ".NsNowDogService")
        self.assertEqual(service.get(android + "permission"), "android.permission.BIND_ACCESSIBILITY_SERVICE")
        config = ET.parse(ROOT / "app/src/main/res/xml/nsnow_dog_service.xml").getroot()
        self.assertEqual(config.get(android + "packageNames"), "uy.com.nsnow.atv")
        self.assertEqual(config.get(android + "accessibilityEventTypes"), "typeWindowStateChanged|typeWindowContentChanged")
        self.assertEqual(config.get(android + "accessibilityFlags"), "flagReportViewIds|flagIncludeNotImportantViews")
        for forbidden in ("canPerformGestures", "canRequestFilterKeyEvents", "canTakeScreenshot", "canRequestTouchExplorationMode"):
            self.assertNotEqual(config.get(android + forbidden), "true")

if __name__ == "__main__":
    unittest.main()
