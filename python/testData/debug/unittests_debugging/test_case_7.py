import unittest


class TestCase(unittest.TestCase):
    def tearDown(self):
        raise RuntimeError("Uh-oh...")

    def testDummy(self):
        self.assertTrue(True)


if __name__ == '__main__':
    unittest.main()
