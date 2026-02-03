__author__ = 'Ilya.Kazakevich'
import unittest
import time

class TestMe2(unittest.TestCase):
    def test_pass(self):
        time.sleep(1)

    def test_raise(self):
        raise ValueError

class TestMe(unittest.TestCase):
    def test_pass2(self):
        pass

    @unittest.skip("SkipThis")
    def test_skip(self):
        pass