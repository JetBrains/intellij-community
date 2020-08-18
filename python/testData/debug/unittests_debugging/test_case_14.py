import unittest
from foo import bar


class MyTestCase(unittest.TestCase):
    def setUp(self):
        pass

    def testMethod(self):
        self.assertTrue(bar(), True)


if __name__ == '__main__':
    unittest.main()
