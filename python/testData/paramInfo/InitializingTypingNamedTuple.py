import typing
from typing import List


MyTup2 = typing.NamedTuple("MyTup2", bar=int, baz=str)
MyTup3 = typing.NamedTuple("MyTup2", [("bar", int), ("baz", str)])


class MyTup4(typing.NamedTuple):
    bar: int
    baz: str


class MyTup5(typing.NamedTuple):
    bar: int
    baz: str
    foo = 5


class MyTup6(typing.NamedTuple):
    bar: int
    baz: str
    foo: int


MyTup7 = typing.NamedTuple("MyTup7", names=List[str], ages=List[int])


class MyTup8(typing.NamedTuple):
    bar: int
    baz: str = ""


MyTup2(<arg1>)
MyTup3(<arg2>)
MyTup4(<arg3>)
MyTup5(<arg4>)
MyTup6(<arg5>)
MyTup7(<arg6>)
MyTup8(<arg7>)
