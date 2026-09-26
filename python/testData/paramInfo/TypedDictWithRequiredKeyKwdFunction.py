from typing import Required, TypedDict, Unpack


class Movie(TypedDict, total=False):
    name: str
    year: Required[int]


def foo(a: int, **b: Unpack[Movie]):
    pass


foo(<arg1>-1, <arg2>name="", <arg3>year=2000)