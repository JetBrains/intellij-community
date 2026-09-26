"""
Tests the slots functionality of dataclass added in Python 3.10.
"""

# Specification: https://docs.python.org/3/library/dataclasses.html#module-contents

from dataclasses import dataclass

# This should generate an error because __slots__ is already defined.
@dataclass(slots=True)  # E[DC1]
class DC1:  # E[DC1]
    x: int

    __slots__ = ()


@dataclass(slots=True)
class DC2:
    x: int

    def __init__(self):
        self.x = 3

        # This should generate an error because "y" is not in slots.
        self.y = 3  # E


@dataclass(slots=False)
class DC3:
    x: int

    __slots__ = ("x",)

    def __init__(self):
        self.x = 3

        # This should generate an error because "y" is not in slots.
        self.y = 3  # E


@dataclass
class DC4:
    __slots__ = ("y", "x")
    x: int
    y: str


DC4(1, "bar")


@dataclass(slots=True)
class DC5:
    a: int


DC5.__slots__
DC5(1).__slots__


@dataclass
class DC6:
    a: int


# This should generate an error because __slots__ is not defined.
DC6.__slots__  # E

# This should generate an error because __slots__ is not defined.
DC6(1).__slots__  # E
