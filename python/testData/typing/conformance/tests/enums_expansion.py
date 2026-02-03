"""
Tests that the type checker handles literal expansion of enum classes.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/enums.html#enum-literal-expansion

from enum import Enum, Flag
from typing import Literal, Never, assert_type

# > From the perspective of the type system, most enum classes are equivalent
# > to the union of the literal members within that enum. Type checkers may
# > therefore expand an enum type


class Color(Enum):
    RED = 1
    GREEN = 2
    BLUE = 3


def print_color1(c: Color):
    if c is Color.RED or c is Color.BLUE:
        print("red or blue")
    else:
        assert_type(c, Literal[Color.GREEN])  # E?


def print_color2(c: Color):
    match c:
        case Color.RED | Color.BLUE:
            print("red or blue")
        case Color.GREEN:
            print("green")
        case _:
            assert_type(c, Never)  # E?


# > This rule does not apply to classes that derive from enum. Flag because
# > these enums allow flags to be combined in arbitrary ways.


class CustomFlags(Flag):
    FLAG1 = 1
    FLAG2 = 2
    FLAG3 = 4


def test1(f: CustomFlags):
    if f is CustomFlags.FLAG1 or f is CustomFlags.FLAG2:
        print("flag1 and flag2")
    else:
        assert_type(f, CustomFlags)
        assert_type(f, Literal[CustomFlags.FLAG3])  # E


def test2(f: CustomFlags):
    match f:
        case CustomFlags.FLAG1 | CustomFlags.FLAG2:
            pass
        case CustomFlags.FLAG3:
            pass
        case _:
            assert_type(f, CustomFlags)


# > A type checker should treat a complete union of all literal members as
# > compatible with the enum type.


class Answer(Enum):
    Yes = 1
    No = 2


def test3(val: object) -> list[Answer]:
    assert val is Answer.Yes or val is Answer.No
    x = [val]
    return x
