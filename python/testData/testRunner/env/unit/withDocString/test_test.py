from unittest import TestCase

class SomeTestCase(TestCase):
    def testSomething(self):
        """ Only with docstring test is parsed with extra space"""
        self.assertEqual(1 + 1, 2)

    def testSomethingBad(self):
        """Fail"""
        self.assertEqual(1 + 1, 3)
