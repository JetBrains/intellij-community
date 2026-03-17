import unittest
from unittest.mock import patch

from example_module import MyClass


class TestPatchObject(unittest.TestCase):

    @patch.object(MyClass, "my_method")
    def test_patch_object_method(self, mock_method):
        pass

    @patch.object(MyClass, "class_attr")
    def test_patch_object_attr(self, mock_attr):
        pass

    @patch.object(MyClass, "static_method")
    def test_patch_object_static(self, mock_static):
        pass

    @patch.object(MyClass, "does_not_exist")
    def test_patch_object_unresolved(self, mock_bad):
        pass

    @patch.object(MyClass, "does_not_exist", create=True)
    def test_patch_object_create_true(self, mock_created):
        pass

    def test_with_patch_object(self):
        with patch.object(MyClass, "my_method") as mock_method:
            pass
