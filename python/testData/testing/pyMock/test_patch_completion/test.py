import unittest
from unittest.mock import patch


class TestPatchCompletion(unittest.TestCase):

    @patch("example_module.MyClass.<caret>")
    def test_completion(self, mock_obj):
        pass
