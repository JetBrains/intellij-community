"""
Tests unpack operations for TypeVarTuple.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#unpacking-tuple-types

from typing import Any, Generic, NewType, TypeVarTuple

Height = NewType("Height", int)
Width = NewType("Width", int)
Batch = NewType("Batch", int)
Channels = NewType("Channels", int)

Ts = TypeVarTuple("Ts")


class Array(Generic[*Ts]):
    ...


def process_batch_channels(x: Array[Batch, *tuple[Any, ...], Channels]) -> None:
    ...


def func3(
    x: Array[Batch, Height, Width, Channels], y: Array[Batch, Channels], z: Array[Batch]
):
    process_batch_channels(x)  # OK
    process_batch_channels(y)  # OK
    process_batch_channels(z)  # E


Shape = TypeVarTuple("Shape")


def expect_variadic_array(x: Array[Batch, *Shape]) -> None:
    ...


def expect_precise_array(x: Array[Batch, Height, Width, Channels]) -> None:
    ...


def func4(y: Array[*tuple[Any, ...]]):
    expect_variadic_array(y)  # OK
    expect_precise_array(y)  # OK
