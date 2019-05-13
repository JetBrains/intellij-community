from unittest import TestCase


class MyTest(TestCase):
    def test_pass(self):
        self.assertEqual(1 + 1, 2)
