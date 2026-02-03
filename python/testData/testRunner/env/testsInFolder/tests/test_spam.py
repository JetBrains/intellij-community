from unittest import TestCase


def test_funeggs():
    print("I am function")


class EggsTest(TestCase):
    def test_metheggs(self):
        print("I am method")



class Parent:

    def test_first(self):
        assert True

    def test_second(self):
        assert False


class Child(TestCase, Parent):
    pass