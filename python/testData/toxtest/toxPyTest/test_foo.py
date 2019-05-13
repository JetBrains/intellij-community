from unittest import TestCase


class TheTest(TestCase):

    def test_arithmetic(self):
        assert 4 == 5

    def test_orthography(self):
        assert 'a' + 'a' == 'A'

    def test_ok(self):
        print 1
