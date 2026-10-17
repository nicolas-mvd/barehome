"""Host tests for strict requests, authenticated replies, subnet checks and replay cache."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class PictureStateWireTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK is required")
    def test_wire_and_recent_request_cache(self):
        with tempfile.TemporaryDirectory(prefix="barehome-state-tests-") as classes:
            sources = [ROOT / "app/src/main/java/uy/nico/barehome" / name
                       for name in ("PictureStateWire.java", "PictureRequestCache.java")]
            sources.append(ROOT / "tests/PictureStateWireTest.java")
            subprocess.run(["javac", "--release", "8", "-d", classes, *map(str, sources)],
                           check=True, capture_output=True, text=True)
            result = subprocess.run(["java", "-cp", classes, "uy.nico.barehome.PictureStateWireTest"],
                                    check=True, capture_output=True, text=True)
            self.assertIn("behavioral assertions passed", result.stdout)
            print(result.stdout.strip())


if __name__ == "__main__":
    unittest.main()
