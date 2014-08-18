from unittest import TestCase

class UTests(TestCase):
    def testOne(self):
        self.assertEqual(4, 2*2)

    def testTwo(self):
        self.assertTrue(False or True)
