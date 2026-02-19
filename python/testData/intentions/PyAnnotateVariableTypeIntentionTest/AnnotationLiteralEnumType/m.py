from enum import EnumType
from typing import Literal


class MyEnum(metaclass=EnumType):
    A = 1
    B = 2


def foo_bar() -> Literal[MyEnum.A]: ...