from __future__ import annotations

import copy
import sys
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
