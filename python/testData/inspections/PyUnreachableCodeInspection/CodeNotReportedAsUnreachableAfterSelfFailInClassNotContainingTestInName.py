# PY-23859

from unittest import TestCase

class C(TestCase):
    def test_1(self):
        self.fail()
        return -42