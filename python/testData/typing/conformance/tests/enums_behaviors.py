"""
Tests basic behaviors of of Enum classes.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/enums.html#enum-definition

from enum import Enum
from typing import assert_type, Literal

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

# 'Literal[Color.RED]' and 'Color' are both acceptable
assert_type(Color["RED"], Color)  # E[red]
assert_type(Color["RED"], Literal[Color.RED])  # E[red]

# 'Literal[Color.BLUE]' and 'Color' are both acceptable
assert_type(Color(3), Color)  # E[blue]
assert_type(Color(3), Literal[Color.BLUE])  # E[blue]


# > An Enum class with one or more defined members cannot be subclassed.

class EnumWithNoMembers(Enum):
    pass

class Shape(EnumWithNoMembers):  # OK (because no members are defined)
    SQUARE = 1
    CIRCLE = 2

class ExtendedShape(Shape):  # E: Shape is implicitly final
    TRIANGLE = 3
