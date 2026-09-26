import unittest
from unittest.mock import patch


class TestNav(unittest.TestCase):

    @patch("example_module.MyClass.my_me<caret>thod")
    def test_patch_method(self, mock_method):
        pass
