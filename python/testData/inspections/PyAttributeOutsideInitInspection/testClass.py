__author__ = 'ktisha'

from unittest import TestCase
class TestA(TestCase):

    def setUp(self):
        self.b = 1

    def test_b(self):
        self.assertEquals(self.b, 1)
