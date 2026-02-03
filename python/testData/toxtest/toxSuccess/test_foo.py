from unittest import TestCase
import sys


class TheTest(TestCase):

    def test_arithmetic(self):
        assert 4 == 5

    def test_orthography(self):
        assert 'a' + 'a' == 'A'

    def test_ok(self):
        print("I am " + str(sys.version))
        pass
