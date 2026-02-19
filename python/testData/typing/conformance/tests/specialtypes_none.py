"""
Tests the handling of builtins.None in a type annotation.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/special-types.html#none

from types import NoneType
from typing import Hashable, Iterable, assert_type


# > When used in a type hint, the expression None is considered equivalent to type(None).


def func1(val1: None) -> None:
    assert_type(val1, None)
    t1: None = None
    return None  # OK


func1(None)  # OK
func1(type(None))  # E

# None is hashable
none1: Hashable = None  # OK

# None is not iterable
none2: Iterable = None  # E: not iterable


None.__class__  # OK
None.__doc__  # OK
None.__eq__(0)  # OK


def func2(val1: type[None]):
    assert_type(val1, type[None])


func2(None.__class__)  # OK
func2(type(None))  # OK
func2(None)  # E: not compatible
