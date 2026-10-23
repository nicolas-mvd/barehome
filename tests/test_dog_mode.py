"""Transaction, persistent rollback, strict command and reply regression tests."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class DogModeTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK is required")
    def test_transaction_and_wire(self):
        with tempfile.TemporaryDirectory(prefix="barehome-dog-tests-") as classes:
            sources = [ROOT / "app/src/main/java/uy/nico/barehome" / name for name in
                       ("PictureStateWire.java", "DogModeWire.java", "DogModeTransaction.java", "DogModeSetup.java")]
            sources.append(ROOT / "tests/DogModeTest.java")
            subprocess.run(["javac", "--release", "8", "-d", classes, *map(str, sources)],
                           check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", classes, "uy.nico.barehome.DogModeTest"],
                                    check=True, capture_output=True, text=True)
            self.assertIn("behavioral assertions passed", result.stdout)
            print(result.stdout.strip())


if __name__ == "__main__":
    unittest.main()
