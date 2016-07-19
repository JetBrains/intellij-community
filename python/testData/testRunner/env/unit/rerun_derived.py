import unittest

from unittest import TestCase

class BaseTestCases:
    class TestAbstract(TestCase):
        def test_a(self):
            raise Exception()


class TestDerived(BaseTestCases.TestAbstract):
    def test_b(self):
        print("Running from derived class")