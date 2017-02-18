from unittest import TestCase


class FooTest(TestCase):
    def test_test(self):
        raise Exception("\033[31mError Occurred")