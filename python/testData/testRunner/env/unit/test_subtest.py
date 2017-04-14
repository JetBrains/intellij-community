from unittest import TestCase


class SpamTest(TestCase):
    def test_test(self):
        for i in range(0, 10):
            with self.subTest(i=i):
                print(1)
                self.assertTrue(i % 2)