from unittest import TestCase


class TestThis(TestCase):
    def test_this(self):
        with self.subTest('test'):
            self.assertEqual("D", "a", "b")
