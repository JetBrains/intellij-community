"""
Tests type checking of the __post_init__ method in a dataclass.
"""

# Specification: https://peps.python.org/pep-0557/#post-init-processing

from dataclasses import InitVar, dataclass, field, replace
from typing import assert_type


@dataclass
class DC1:
    a: int
    b: int
    x: InitVar[int]
    c: int
    y: InitVar[str]

    def __post_init__(self, x: int, y: int) -> None:  # E: wrong type for y
        pass


dc1 = DC1(1, 2, 3, 4, "")

assert_type(dc1.a, int)
assert_type(dc1.b, int)
assert_type(dc1.c, int)
print(dc1.x)  # E: cannot access InitVar
print(dc1.y)  # E: cannot access InitVar

@dataclass
class DC2:
    x: InitVar[int]
    y: InitVar[str]

    def __post_init__(self, x: int) -> None:  # E: missing y
        pass


@dataclass
class DC3:
    _name: InitVar[str] = field()
    name: str = field(init=False)

    def __post_init__(self, _name: str):
        ...


@dataclass
class DC4(DC3):
    _age: InitVar[int] = field()
    age: int = field(init=False)

    def __post_init__(self, _name: str, _age: int):
        ...
