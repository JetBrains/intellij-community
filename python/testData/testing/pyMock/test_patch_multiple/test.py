import unittest
from unittest.mock import patch, DEFAULT


class TestPatchMultiple(unittest.TestCase):

    @patch.multiple("example_module", top_level_function=DEFAULT)
    def test_patch_multiple_positional(self, **kwargs):
        pass

    @patch.multiple(target="example_module", top_level_function=DEFAULT)
    def test_patch_multiple_keyword(self, **kwargs):
        pass

    def test_with_patch_multiple(self):
        with patch.multiple("example_module", top_level_function=DEFAULT):
            pass
