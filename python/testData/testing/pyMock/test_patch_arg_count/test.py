import unittest
from unittest.mock import patch, MagicMock


class TestPatchArgCount(unittest.TestCase):

    @patch("example_module.MyClass")
    def test_correct_count(self, mock_class):
        pass

    @patch("example_module.MyClass")
    @patch("example_module.top_level_function")
    def test_correct_count_stacked(self, mock_func, mock_class):
        pass

    @patch("example_module.MyClass")
    def test_too_few_params<warning descr="Function needs 1 more parameter(s) for @patch injected mocks">(self)</warning>:
        pass

    @patch("example_module.MyClass")
    @patch("example_module.top_level_function")
    def test_too_few_params_stacked<warning descr="Function needs 1 more parameter(s) for @patch injected mocks">(self, mock_func)</warning>:
        pass

    @patch("example_module.MyClass")
    def test_too_many_params<warning descr="Function has 1 extra parameter(s) not matched by @patch decorators">(self, mock_class, extra_param)</warning>:
        pass

    @patch("example_module.MyClass", new="replacement")
    def test_new_keyword_no_injection(self):
        pass

    @patch("example_module.MyClass", MagicMock())
    def test_new_positional_no_injection(self):
        pass

    REPLACEMENT = object()

    @patch("example_module.MyClass", REPLACEMENT)
    def test_new_positional_variable_no_injection(self):
        pass

    @patch("example_module.MyClass")
    def test_star_args(self, *args):
        pass

    @patch("example_module.MyClass")
    def test_kwargs(self, **kwargs):
        pass
