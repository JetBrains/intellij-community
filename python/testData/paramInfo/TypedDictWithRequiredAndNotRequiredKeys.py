from typing_extensions import TypedDict, Required, NotRequired


class A(TypedDict):
    x: int
    y: NotRequired[int]


class B(TypedDict, total=False):
    x: Required[int]
    y: int


a = A(x=<arg1>)
b = B(x=<arg2>)
