#!/usr/bin/env python

# from falcon import testing
import unittest


# class TestAPI(testing.TestCase):

class TestAPI(unittest.TestCase):
    def setUp(self):

        super().setUp()


class TestCoverage(TestAPI):
    def test_first(self):

        self.assertTrue(1 == 1)

    def test_second(self):

        self.assertFalse(1 == 1)


if __name__ == '__main__':
    unittest.main()
