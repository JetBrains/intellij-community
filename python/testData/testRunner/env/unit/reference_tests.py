__author__ = 'Ilya.Kazakevich'
import unittest

class SpamTest(unittest.TestCase):

    def test_1(self):
        self.fail()


class SpamTest2(unittest.TestCase):

    def test_2(self):
        assert False