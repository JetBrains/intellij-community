from unittest import TestCase


def test_test():
    assert False


class FooTest(TestCase):
    def test_test(self):
        self.fail()