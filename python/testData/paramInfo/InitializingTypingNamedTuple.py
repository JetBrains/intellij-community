import typing


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


MyTup2(<arg1>)
MyTup3(<arg2>)
MyTup4(<arg3>)
MyTup5(<arg4>)
MyTup6(<arg5>)
