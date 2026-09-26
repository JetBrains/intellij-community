"""
Tests the typing.TYPE_CHECKING constant.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/directives.html#type-checking

from typing import TYPE_CHECKING, assert_type


if not TYPE_CHECKING:
    a: int = "" # This should not generate an error

if TYPE_CHECKING:
    b: list[int] = [1, 2, 3]
else:
    b: list[str] = ["a", "b", "c"]

assert_type(b, list[int])
