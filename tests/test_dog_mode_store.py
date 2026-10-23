"""Exercise production file-backed journal reads and failed replacement across strategy upgrades."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
STUBS = {
    "android/content/Context.java": '''package android.content; import java.io.File;
public final class Context {private final File files; public Context(File files){this.files=files;files.mkdirs();}
public File getFilesDir(){return files;}}''',
    "android/util/AtomicFile.java": '''package android.util; import java.io.*; import java.nio.file.*;
public final class AtomicFile {public static boolean failFinish; private final File base,temp;
public AtomicFile(File base){this.base=base;temp=new File(base.getPath()+".new");}
public FileInputStream openRead() throws FileNotFoundException{return new FileInputStream(base);}
public FileOutputStream startWrite() throws IOException{return new FileOutputStream(temp);}
public void finishWrite(FileOutputStream output){try{output.close();if(failFinish){temp.delete();return;}
Files.move(temp.toPath(),base.toPath(),StandardCopyOption.REPLACE_EXISTING);}catch(IOException ignored){}}
public void failWrite(FileOutputStream output){try{output.close();}catch(IOException ignored){}temp.delete();}}''',
}


class DogModeStoreTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK is required")
    def test_legacy_and_five_value_disk_records(self):
        with tempfile.TemporaryDirectory(prefix="barehome-dog-store-tests-") as temporary:
            folder = Path(temporary)
            sources = []
            for name, content in STUBS.items():
                path = folder / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content)
                sources.append(path)
            sources += [ROOT / "app/src/main/java/uy/nico/barehome" / name for name in
                        ("DogModeStore.java", "DogModeTransaction.java")]
            sources.append(ROOT / "tests/DogModeStoreTest.java")
            subprocess.run(["javac", "--release", "8", "-d", str(folder / "classes"), *map(str, sources)],
                           check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", str(folder / "classes"), "uy.nico.barehome.DogModeStoreTest", str(folder / "private")],
                                    check=True, capture_output=True, text=True)
            self.assertIn("behavioral assertions passed", result.stdout)
            print(result.stdout.strip())


if __name__ == "__main__":
    unittest.main()
