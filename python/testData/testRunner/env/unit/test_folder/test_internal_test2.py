from unittest import TestCase

class UTests(TestCase):
    def testOne(self):
        self.assertEqual(5, 2*2)

    def testTwo(self):
        self.assertTrue(False)

    def testThree(self):
        self.assertTrue(True)
