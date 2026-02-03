__author__ = 'ktisha'

import unittest

class A:
    def __init__(self):
        self._x = 1

    def _foo(self):
        pass


class TestA(unittest.TestCase):
    def testA(self):
        a = A()
        a._foo()
        print(a._x)
