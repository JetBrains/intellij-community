import unittest
from unittest.mock import patch


class TestPatchPackageNavigation(unittest.TestCase):

    @patch("example_package.submodule.SubClass.sub_method")
    def test_patch_through_package(self, mock_method):
        pass
