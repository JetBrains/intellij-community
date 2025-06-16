from enum import Enum, member, nonmember
from typing import Any


class Color(Enum):
    R = 1
    G = 2
    RED = R
    foo = nonmember(3)
    @member
    def bar(self): ...


class A:
    X = Color.R


class SuperEnum(Enum):
    PINK = "PINK", "hot"
    FLOSS = "FLOSS", "sweet"


tuple = 1, "ab"
o = object()
def get_object() -> object: ...
def get_any() -> Any: ...


class E(Enum):
    FOO = tuple
    BAR = o
    BUZ = get_object()
    QUX = get_any()

    def meth(self): ...

    meth2 = meth

