from typing import NotRequired, TypedDict, Unpack


class Movie(TypedDict):
    name: NotRequired[str]
    year: int


def foo(a: int, **b: Unpack[Movie]):
    pass


foo(<arg1>-1, <arg2>name="", <arg3>year=2000)