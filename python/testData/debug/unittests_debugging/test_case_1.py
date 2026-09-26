import unittest


def external_resource_available():
    return False


class MyTestCase(unittest.TestCase):
    @unittest.expectedFailure
    def test_fail(self):
        self.assertEqual(1, 0, "broken")

    def test_even(self):
        for i in range(0, 6):
            with self.subTest(i=i):
                self.assertEqual(i % 2, 0)

    def test_maybe_skipped(self):
        if not external_resource_available():
            self.skipTest("external resource not available")
        pass


if __name__ == '__main__':
    unittest.main()
