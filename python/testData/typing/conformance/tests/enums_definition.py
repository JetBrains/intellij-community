"""
Tests handling of Enum class definitions using the class syntax.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/enums.html#enum-definition

from enum import Enum, EnumType
from typing import Literal, assert_type

# > Type checkers should support the class syntax


class Color1(Enum):
    RED = 1
    GREEN = 2
    BLUE = 3


assert_type(Color1.RED, Literal[Color1.RED])


# > The function syntax (in its various forms) is optional

Color2 = Enum("Color2", "RED", "GREEN", "BLUE")  # E?
Color3 = Enum("Color3", ["RED", "GREEN", "BLUE"])  # E?
Color4 = Enum("Color4", ("RED", "GREEN", "BLUE"))  # E?
Color5 = Enum("Color5", "RED, GREEN, BLUE")  # E?
Color6 = Enum("Color6", "RED GREEN BLUE")  # E?
Color7 = Enum("Color7", [("RED", 1), ("GREEN", 2), ("BLUE", 3)])  # E?
Color8 = Enum("Color8", (("RED", 1), ("GREEN", 2), ("BLUE", 3)))  # E?
Color9 = Enum("Color9", {"RED": 1, "GREEN": 2, "BLUE": 3})  # E?

assert_type(Color2.RED, Literal[Color2.RED])  # E?
assert_type(Color3.RED, Literal[Color3.RED])  # E?
assert_type(Color4.RED, Literal[Color4.RED])  # E?
assert_type(Color5.RED, Literal[Color5.RED])  # E?
assert_type(Color6.RED, Literal[Color6.RED])  # E?
assert_type(Color7.RED, Literal[Color7.RED])  # E?
assert_type(Color8.RED, Literal[Color8.RED])  # E?
assert_type(Color9.RED, Literal[Color9.RED])  # E?


# > Enum classes can also be defined using a subclass of enum.Enum or any class
# > that uses enum.EnumType (or a subclass thereof) as a metaclass.
# > Type checkers should treat such classes as enums


class CustomEnum1(Enum):
    pass


class Color10(CustomEnum1):
    RED = 1
    GREEN = 2
    BLUE = 3


assert_type(Color10.RED, Literal[Color10.RED])


class CustomEnumType(EnumType):
    pass


class CustomEnum2(metaclass=CustomEnumType):
    pass


class Color11(CustomEnum2):
    RED = 1
    GREEN = 2
    BLUE = 3


assert_type(Color11.RED, Literal[Color11.RED])
