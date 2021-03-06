import unittest


def f():
    raise RuntimeError("Boom!")


class MyTestCase(unittest.TestCase):
    def setUp(self):
        self.x = f()

    def testDummy(self):
        self.assertTrue(True)
