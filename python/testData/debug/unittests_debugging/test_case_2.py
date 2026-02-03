import unittest


class MyTestCase(unittest.TestCase):
    def testFail(self):
        self.assertTrue(False)


if __name__ == '__main__':
    unittest.main()