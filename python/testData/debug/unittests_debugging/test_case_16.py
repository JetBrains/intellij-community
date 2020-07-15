import unittest


class TestCase(unittest.TestCase):
    def test_fail(self):
        d = 1 / 0
        self.assertEqual(1, 0, "broken")
