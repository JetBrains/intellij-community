import unittest
from unittest.mock import patch


class TestPatchReferences(unittest.TestCase):

    @patch("example_module.MyClass")
    def test_patch_class(self, mock_class):
        pass

    @patch("example_module.MyClass.my_method")
    def test_patch_method(self, mock_method):
        pass

    @patch("example_module.top_level_function")
    def test_patch_function(self, mock_func):
        pass

    @patch("example_module.DoesNotExist")
    def test_unresolved(self, mock_unresolved):
        pass

    @patch("example_module.DoesNotExist", create=True)
    def test_create_true(self, mock_created):
        pass

    @patch(target="example_module.MyClass")
    def test_patch_class_with_target_kw(self, mock_class):
        pass

    def test_with_patch_class(self):
        with patch("example_module.MyClass") as mock_class:
            pass

    def test_with_patch_method(self):
        with patch("example_module.MyClass.my_method") as mock_method:
            pass

    def test_with_patch_target_kw(self):
        with patch(target="example_module.MyClass") as mock_class:
            pass
