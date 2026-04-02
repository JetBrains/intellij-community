import os
import subprocess
import sys
import unittest

from testing import _helpers_root


class VirtualenvPy3Test(unittest.TestCase):
    def test_pyz_integrity(self):
        result = subprocess.run(
            [sys.executable, os.path.join(_helpers_root, "virtualenv-py3.pyz"), "--version"],
            capture_output=True, text=True
        )
        self.assertEqual(result.returncode, 0)
        self.assertIn("virtualenv", result.stdout)
