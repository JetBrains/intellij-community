"""
Tests the use of TypeVarTuple in function overloads.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#overloads-for-accessing-individual-types

from typing import Any, Generic, TypeVar, TypeVarTuple, assert_type, overload


Shape = TypeVarTuple("Shape")
Axis1 = TypeVar("Axis1")
Axis2 = TypeVar("Axis2")
Axis3 = TypeVar("Axis3")


class Array(Generic[*Shape]):
    @overload
    def transpose(self: "Array[Axis1, Axis2]") -> "Array[Axis2, Axis1]":
        ...

    @overload
    def transpose(self: "Array[Axis1, Axis2, Axis3]") -> "Array[Axis3, Axis2, Axis1]":
        ...

    def transpose(self) -> Any:
        pass


def func1(a: Array[Axis1, Axis2], b: Array[Axis1, Axis2, Axis3]):
    assert_type(a.transpose(), Array[Axis2, Axis1])
    assert_type(b.transpose(), Array[Axis3, Axis2, Axis1])
