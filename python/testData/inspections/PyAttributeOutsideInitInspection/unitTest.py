__author__ = 'ktisha'

from unittest import TestCase
class MyTestCase(TestCase):
    def setUp(self):
        self.my = 1

    def test(self):
        self.my = 2