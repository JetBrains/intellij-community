class C(object):
    def __enter__(self):
        return self

    def __exit__(self, exc, value, traceback):
        return True


def f11():
    with C():
        raise Exception()
    print(1) #pass


def g2():
    raise Exception()


def f12():
    with C():
        return g2()
    <warning descr="This code is unreachable">print(1) #pass</warning>


class A1(TestCase):
    def f3(self):
        with C():
            g2()
        print(1) #pass


import contextlib
from contextlib import suppress
from unittest import TestCase


def f21():
    with suppress(Exception):
        raise Exception()
    print(1) #pass


def f22():
    with contextlib.suppress(Exception):
        return g2()
    print(1) #pass


class A2(TestCase):
    def f3(self):
        with self.assertRaises(Exception):
            g2()
        print(1) #pass