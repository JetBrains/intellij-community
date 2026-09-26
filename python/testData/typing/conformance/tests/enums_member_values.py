"""
Tests that the type checker handles the `_value_` and `value` attributes correctly.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/enums.html#member-values

# > All enum member objects have an attribute _value_ that contains the
# > member’s value. They also have a property value that returns the same value.
# > Type checkers may infer the type of a member’s value.

from enum import Enum, auto
from typing import Literal, assert_type


class Color(Enum):
    RED = 1
    GREEN = 2
    BLUE = 3


assert_type(Color.RED._value_, Literal[1])  # E?
assert_type(Color.RED.value, Literal[1])  # E?


def func1(red_or_blue: Literal[Color.RED, Color.BLUE]):
    assert_type(red_or_blue.value, Literal[1, 3])  # E?


def func2(any_color: Color):
    assert_type(any_color.value, Literal[1, 2, 3])  # E?


# > The value of _value_ can be assigned in a constructor method. This
# > technique is sometimes used to initialize both the member value and
# > non-member attributes. If the value assigned in the class body is a tuple,
# > the unpacked tuple value is passed to the constructor. Type checkers may
# > validate consistency between assigned tuple values and the constructor
# > signature.


class Planet(Enum):
    def __init__(self, value: int, mass: float, radius: float):
        self._value_ = value
        self.mass = mass
        self.radius = radius

    MERCURY = (1, 3.303e23, 2.4397e6)
    VENUS = (2, 4.869e24, 6.0518e6)
    EARTH = (3, 5.976e24, 6.37814e6)
    MARS = (6.421e23, 3.3972e6)  # E?: Type checker error (optional)
    JUPITER = 5  # E?: Type checker error (optional)


assert_type(Planet.MERCURY.value, Literal[1])  # E?


# > The class enum.auto and method _generate_next_value_ can be used within
# > an enum class to automatically generate values for enum members.
# > Type checkers may support these to infer literal types for member values.


class Color2(Enum):
    RED = auto()
    GREEN = auto()
    BLUE = auto()


assert_type(Color2.RED.value, Literal[1])  # E?

# > If an enum class provides an explicit type annotation for _value_, type
# > checkers should enforce this declared type when values are assigned to
# > _value_.


class Color3(Enum):
    _value_: int
    RED = 1  # OK
    GREEN = "green"  # E


class Planet2(Enum):
    _value_: str

    def __init__(self, value: int, mass: float, radius: float):
        self._value_ = value  # E

    MERCURY = (1, 3.303e23, 2.4397e6)


from _enums_member_values import ColumnType

# > If the literal values for enum members are not supplied, as they sometimes
# > are not within a type stub file, a type checker can use the type of the
# > _value_ attribute.

assert_type(ColumnType.DORIC.value, int)  # E?
