# PY-23859, PY-3886

from unittest import TestCase

class TestSomething(TestCase):
    def test_1(self):
        self.fail()
        <warning descr="This code is unreachable">return -42</warning>

class SomethingTest(TestCase):
    def test_1(self):
        self.fail()
        <warning descr="This code is unreachable">return -42</warning>