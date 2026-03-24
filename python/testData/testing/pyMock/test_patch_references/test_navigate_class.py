import unittest
from unittest.mock import patch


class TestNav(unittest.TestCase):

    @patch("example_module.MyC<caret>lass")
    def test_patch_class(self, mock_class):
        pass
