from collections import namedtuple

def f():
    Point = namedtuple("Point", ["x1", "x2"], verbose=True)
    <weak_warning descr="Variable in function should be lowercase">Test</weak_warning> = "foo"