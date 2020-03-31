from __future__ import print_function


def make_class(x):
    class C:
        p = x

    return C


def foo():
    return 100


class D(make_class(foo())):  #  breakpoint
    def __init__(self):
        self.d = 3

    def foo(self):
        return 1