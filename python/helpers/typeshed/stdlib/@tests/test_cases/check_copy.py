from __future__ import annotations

import copy
import sys
from typing import Generic, TypeVar
from typing_extensions import Self, assert_type


class ReplaceableClass:
    def __init__(self, val: int) -> None:
        self.val = val

    def __replace__(self, val: int) -> Self:
        cpy = copy.copy(self)
        cpy.val = val
        return cpy


if sys.version_info >= (3, 13):
    obj = ReplaceableClass(42)
    cpy = copy.replace(obj, val=23)
    assert_type(cpy, ReplaceableClass)


_T_co = TypeVar("_T_co", covariant=True)


class Box(Generic[_T_co]):
    def __init__(self, value: _T_co, /) -> None:
        self.value = value

    def __replace__(self, value: str) -> Box[str]:
        return Box(value)


if sys.version_info >= (3, 13):
    box1: Box[int] = Box(42)
    box2 = copy.replace(box1, val="spam")
    assert_type(box2, Box[str])
