"""
Tests inheritance rules involving the update method for TypedDicts
that contain read-only items.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/typeddict.html#read-only-items

from typing import Never, NotRequired, TypedDict
from typing_extensions import ReadOnly

# > In addition to existing type checking rules, type checkers should error if
# > a TypedDict with a read-only item is updated with another TypedDict that
# > declares that key.


class A(TypedDict):
    x: ReadOnly[int]
    y: int


a1: A = {"x": 1, "y": 2}
a2: A = {"x": 3, "y": 4}
a1.update(a2)  # E

# > Unless the declared value is of bottom type (:data:`~typing.Never`).


class B(TypedDict):
    x: NotRequired[Never]
    y: ReadOnly[int]


def update_a(a: A, b: B) -> None:
    a.update(b)  # OK
