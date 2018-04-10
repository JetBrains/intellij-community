import typing


MyTup1 = typing.NamedTuple("MyTup2", bar=int, baz=str)


class MyTup2(typing.NamedTuple):
    bar: int
    baz: str


MyTup1(1, "")._replace(<arg1>)
MyTup2(1, "")._replace(<arg2>)