from typing import NamedTuple


nt = NamedTuple("name", [("field", str)])


def foo(x: nt):
    pass


foo(<warning descr="Expected type 'name', got 'int' instead">5</warning>)
foo(nt(field = "f"))