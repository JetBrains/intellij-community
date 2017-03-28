from unittest import TestCase

from logic import smart_func
from tests_package.test_tools import ANSWER


class TestLogic(TestCase):
    def test_test(self):
        self.assertEqual(ANSWER, smart_func())