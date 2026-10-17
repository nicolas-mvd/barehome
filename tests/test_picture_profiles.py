"""Compile and exercise the Android-independent Day/Night state model."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]


class PictureProfilesTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK is required")
    def test_migration_and_profile_persistence(self):
        with tempfile.TemporaryDirectory(prefix="barehome-profile-tests-") as classes:
            subprocess.run([
                "javac", "--release", "8", "-d", classes,
                str(ROOT / "app/src/main/java/uy/nico/barehome/PictureProfiles.java"),
                str(ROOT / "tests/PictureProfilesTest.java"),
            ], check=True, capture_output=True, text=True)
            result = subprocess.run([
                "java", "-cp", classes, "uy.nico.barehome.PictureProfilesTest",
            ], check=True, capture_output=True, text=True)
            self.assertIn("5 behavioral scenarios passed", result.stdout)


if __name__ == "__main__":
    unittest.main()
