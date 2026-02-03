import unittest
from time import sleep


class MyTest(unittest.TestCase):
    @unittest.skip("demonstrating skipping")
    def test_test(self):
        print("A")

    def test_dtest(self):
        print("A")
        sleep(1)
