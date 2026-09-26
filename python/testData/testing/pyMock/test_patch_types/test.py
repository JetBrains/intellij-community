import unittest
from unittest.mock import patch, AsyncMock, MagicMock


class TestPatchTypes(unittest.TestCase):

    @patch("example_module.MyClass.my_method")
    def test_default_type(self, mock_method):
        pass

    @patch("example_module.MyClass.my_method", new_callable=AsyncMock)
    def test_async_mock_type(self, mock_method):
        pass

    @patch("example_module.MyClass.my_method", new=MagicMock())
    def test_no_injection_when_new(self):
        pass

    @patch("example_module.top_level_function")
    @patch("example_module.MyClass.my_method")
    def test_stacked_patch(self, mock_method, mock_function):
        pass

    @patch("A")
    @patch("B")
    def test_stacked_order(self, mock_b, mock_a):
        pass

    @patch("example_module.MyClass.my_method", MagicMock())
    def test_no_injection_when_positional_new(self):
        pass
