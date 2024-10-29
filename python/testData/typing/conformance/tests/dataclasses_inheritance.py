"""
Tests inheritance rules for dataclasses.
"""

# Specification: https://peps.python.org/pep-0557/#inheritance

from dataclasses import dataclass
from typing import Any, ClassVar


@dataclass
class DC1:
    a: int
    b: str = ""


@dataclass
class DC2(DC1):
    b: str = ""
    a: int = 1


dc2_1 = DC2(1, "")

dc2_2 = DC2()


@dataclass
class DC3:
    x: float = 15.0
    y: str = ""


@dataclass
class DC4(DC3):
    z: tuple[int] = (10,)
    x: float = 15


dc4_1 = DC4(0.0, "", (1,))


@dataclass
class DC5:
    # This should generate an error because a default value of
    # type list, dict, or set generate a runtime error.
    x: list[int] = []


@dataclass
class DC6:
    x: int
    y: ClassVar[int] = 1


@dataclass
class DC7(DC6):
    # This should generate an error because a ClassVar cannot override
    # an instance variable of the same name.
    x: ClassVar[int]  # E

    # This should generate an error because an instance variable cannot
    # override a class variable of the same name.
    y: int  # E
