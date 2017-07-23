import unittest
import sys

print("path[0]={0}".format(sys.path[0]))


class SampleTest(unittest.TestCase):
    def test_true(self):
        self.assertTrue(True)

