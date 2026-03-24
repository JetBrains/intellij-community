import unittest
from unittest.mock import patch


class TestPatchDict(unittest.TestCase):

    @patch.dict("example_module.TOP_LEVEL_VAR", {"key": "value"})
    def test_patch_dict_positional(self):
        pass

    @patch.dict(in_dict="example_module.TOP_LEVEL_VAR")
    def test_patch_dict_keyword(self):
        pass

    def test_with_patch_dict(self):
        with patch.dict("example_module.TOP_LEVEL_VAR", {"key": "value"}):
            pass
