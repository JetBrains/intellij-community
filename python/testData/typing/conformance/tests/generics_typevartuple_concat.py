"""
Tests type concatenation using TypeVarTuples.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#type-concatenation

# > Type variable tuples don’t have to be alone; normal types can be prefixed and/or suffixed.

from typing import Generic, NewType, TypeVar, TypeVarTuple, assert_type


Height = NewType("Height", int)
Width = NewType("Width", int)
Batch = NewType("Batch", int)
Channels = NewType("Channels", int)

Shape = TypeVarTuple("Shape")
Ts = TypeVarTuple("Ts")
T = TypeVar("T")


class Array(Generic[*Ts]):
    ...


def add_batch_axis(x: Array[*Shape]) -> Array[Batch, *Shape]:
    ...


def del_batch_axis(x: Array[Batch, *Shape]) -> Array[*Shape]:
    ...


def add_batch_channels(x: Array[*Shape]) -> Array[Batch, *Shape, Channels]:
    ...


def func1(a: Array[Height, Width]):
    b = add_batch_axis(a)  # OK
    assert_type(b, Array[Batch, Height, Width])
    c = del_batch_axis(b)  # OK
    assert_type(c, Array[Height, Width])
    d = add_batch_channels(a)  # OK
    assert_type(d, Array[Batch, Height, Width, Channels])


def prefix_tuple(x: T, y: tuple[*Ts]) -> tuple[T, *Ts]:
    ...


z = prefix_tuple(x=0, y=(True, "a"))
assert_type(z, tuple[int, bool, str])


def move_first_element_to_last(tup: tuple[T, *Ts]) -> tuple[*Ts, T]:
    return (*tup[1:], tup[0])
