
from unittest2 import TestCase


class SampleTest(TestCase):

    def test_sample(self):
        for i in range(10):
            with self.subTest(i=i):
                self.assertTrue(i > 3)