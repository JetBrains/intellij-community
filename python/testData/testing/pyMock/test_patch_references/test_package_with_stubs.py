import unittest
from unittest.mock import patch


class TestPatchPackageWithStubs(unittest.TestCase):

    @patch("example_pkg_with_stubs.VALUE")
    def test_patch_pkg_with_stubs(self, mock_val):
        pass
