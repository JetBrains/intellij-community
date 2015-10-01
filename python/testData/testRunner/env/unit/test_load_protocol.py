# Checks Load test protocol for unittest
import unittest


class ParametricTestCase(unittest.TestCase):

    def __init__(self, method="run_test", p=0):
        super(ParametricTestCase, self).__init__(method)
        self.p = p

    def run_test(self):
        self.assertGreater(self.p, 0)

    def __str__(self):
        return "ParametricTestCase(p={0.p})".format(self)

    @staticmethod
    def suite():

        return unittest.TestSuite([

            ParametricTestCase(p=1),
            ParametricTestCase(p=2),
            ParametricTestCase(p=3)

        ])


def load_tests(loader, tests, pattern):

    suite = ParametricTestCase.suite()
    tests.addTests(suite)

    return tests


if __name__ == "__main__":
    unittest.main()