"""Scoped NS NOW UI actions; no Android device or account data is used."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

class NsNowDogTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK is required")
    def test_fixed_selector_and_prompt_policy(self):
        with tempfile.TemporaryDirectory(prefix="barehome-nsnow-tests-") as classes:
            sources = [ROOT / "app/src/main/java/uy/nico/barehome/NsNowDogSelector.java",
                       ROOT / "tests/NsNowDogSelectorTest.java"]
            subprocess.run(["javac", "--release", "8", "-d", classes, *map(str, sources)],
                           check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", classes, "uy.nico.barehome.NsNowDogSelectorTest"],
                                    check=True, capture_output=True, text=True)
            self.assertIn("behavioral assertions passed", result.stdout)
            print(result.stdout.strip())

if __name__ == "__main__":
    unittest.main()
