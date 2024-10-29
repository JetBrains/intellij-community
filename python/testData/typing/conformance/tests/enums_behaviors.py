"""
Tests basic behaviors of of Enum classes.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/enums.html#enum-definition

from enum import Enum
from typing import assert_type

# > Enum classes are iterable and indexable, and they can be called with a
# > value to look up the enum member with that value. Type checkers should
# > support these behaviors

class Color(Enum):
    RED = 1
    GREEN = 2
    BLUE = 3

for color in Color:
    assert_type(color, Color)

# > Unlike most Python classes, Calling an enum class does not invoke its
# > constructor. Instead, the call performs a value-based lookup of an
# > enum member.

assert_type(Color["RED"], Color)  # 'Literal[Color.RED]' is also acceptable
assert_type(Color(3), Color)  # 'Literal[Color.BLUE]' is also acceptable


# > An Enum class with one or more defined members cannot be subclassed.

class EnumWithNoMembers(Enum):
    pass

class Shape(EnumWithNoMembers):  # OK (because no members are defined)
    SQUARE = 1
    CIRCLE = 2

class ExtendedShape(Shape):  # E: Shape is implicitly final
    TRIANGLE = 3
