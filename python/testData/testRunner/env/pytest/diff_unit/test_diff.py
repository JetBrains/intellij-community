
import unittest


class TestErrorFail(unittest.TestCase):
    def test_fail_diff(self):
        self.assertEqual("A", "B")