"""
Tests that the type checker handles the `_name_` and `name` attributes correctly.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/enums.html#member-names

from enum import Enum
from typing import Literal, assert_type

# > All enum member objects have an attribute _name_ that contains the member’s
# > name. They also have a property name that returns the same name. Type
# > checkers may infer a literal type for the name of a member


class Color(Enum):
    RED = 1
    GREEN = 2
    BLUE = 3


assert_type(Color.RED._name_, Literal["RED"])  # E?
assert_type(Color.RED.name, Literal["RED"])  # E?


def func1(red_or_blue: Literal[Color.RED, Color.BLUE]):
    assert_type(red_or_blue.name, Literal["RED", "BLUE"])  # E?


def func2(any_color: Color):
    assert_type(any_color.name, Literal["RED", "BLUE", "GREEN"])  # E?
