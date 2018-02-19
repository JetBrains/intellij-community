from typing import NamedTuple


class MyTuple(NamedTuple):
    foo: int


def func():
    return MyTuple(foo=42)
