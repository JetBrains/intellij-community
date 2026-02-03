"""
Tests legal and illegal parameterizations of Literal.
"""

# > Literal must be parameterized with at least one type.

from typing import Any, Literal, TypeVar
from enum import Enum


class Color(Enum):
    RED = 0
    GREEN = 1
    BLUE = 2


good1: Literal[26]
good2: Literal[0x1A]
good3: Literal[-4]
good4: Literal[+5]
good5: Literal["hello world"]
good6: Literal[b"hello world"]
good7: Literal["hello world"]
good8: Literal[True]
good9: Literal[Color.RED]
good10: Literal[None]

ReadOnlyMode = Literal["r", "r+"]
WriteAndTruncateMode = Literal["w", "w+", "wt", "w+t"]
WriteNoTruncateMode = Literal["r+", "r+t"]
AppendMode = Literal["a", "a+", "at", "a+t"]

AllModes = Literal[ReadOnlyMode, WriteAndTruncateMode, WriteNoTruncateMode, AppendMode]

good11: Literal[Literal[Literal[1, 2, 3], "foo"], 5, None]

variable = 3
T = TypeVar("T")

# > Arbitrary expressions [are illegal]
bad1: Literal[3 + 4]  # E
bad2: Literal["foo".replace("o", "b")]  # E
bad3: Literal[4 + 3j]  # E
bad4: Literal[~5]  # E
bad5: Literal[not False]  # E
bad6: Literal[(1, "foo", "bar")]  # E
bad7: Literal[{"a": "b", "c": "d"}]  # E
bad8: Literal[int]  # E
bad9: Literal[variable]  # E
bad10: Literal[T]  # E
bad11: Literal[3.14]  # E
bad12: Literal[Any]  # E
bad13: Literal[...]  # E


def my_function(x: Literal[1 + 2]) -> int:  # E
    return x * 3


x: Literal  # E
y: Literal[my_function] = my_function  # E


def func2(a: Literal[Color.RED]):
    x1: Literal["Color.RED"] = a  # E

    x2: "Literal[Color.RED]" = a  # OK
