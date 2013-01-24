import float
from foo import float
from bar import baz as <warning descr="Shadows a built-in with the same name">float</warning>

def test1(x, <warning descr="Shadows a built-in with the same name">len</warning>, <warning descr="Shadows a built-in with the same name">file</warning>=None):
    foo = 2
    <warning descr="Shadows a built-in with the same name">list</warning> = []
    for <warning descr="Shadows a built-in with the same name">int</warning> in range(10):
        print(int)
    <warning descr="Shadows a built-in with the same name">range</warning> = []
    return [int for <warning descr="Shadows a built-in with the same name">int</warning> in range(10)]


def <warning descr="Shadows a built-in with the same name">list</warning>():
    pass


class <warning descr="Shadows a built-in with the same name">list</warning>(object):
    def foo(self):
        pass

    def list(self):
        pass
