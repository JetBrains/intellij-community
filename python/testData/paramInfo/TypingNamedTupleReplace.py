import typing


MyTup1 = typing.NamedTuple("MyTup1", bar=int, baz=str)


class MyTup2(typing.NamedTuple):
    bar: int
    baz: str


MyTup1(1, "")._replace(<arg1>)
MyTup2(1, "")._replace(<arg2>)

MyTup1._replace(MyTup1(1, ""), <arg3>)
MyTup2._replace(MyTup2(1, ""), <arg4>)