"""Compiled app order survives upgrades from personal to public APKs."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class AppOrderTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK is required")
    def test_personal_order_migrates_and_survives_public_updates(self):
        with tempfile.TemporaryDirectory(prefix="barehome-order-tests-") as classes:
            subprocess.run([
                "javac", "--release", "8", "-d", classes,
                str(ROOT / "app/src/main/java/uy/nico/barehome/AppOrder.java"),
                str(ROOT / "tests/AppOrderTest.java"),
            ], check=True, capture_output=True, text=True)
            result = subprocess.run([
                "java", "-cp", classes, "uy.nico.barehome.AppOrderTest",
            ], check=True, capture_output=True, text=True)
            self.assertIn("7 app order migration scenarios passed", result.stdout)


if __name__ == "__main__":
    unittest.main()
