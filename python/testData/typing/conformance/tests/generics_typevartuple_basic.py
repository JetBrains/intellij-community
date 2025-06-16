"""
Tests basic usage of TypeVarTuple.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#typevartuple

from typing import Generic, NewType, TypeVarTuple, assert_type

Ts = TypeVarTuple("Ts")


class Array1(Generic[*Ts]):
    ...


def func1(*args: *Ts) -> tuple[*Ts]:
    ...


Shape = TypeVarTuple("Shape")


class Array(Generic[*Shape]):
    def __init__(self, shape: tuple[*Shape]):
        self._shape: tuple[*Shape] = shape

    def get_shape(self) -> tuple[*Shape]:
        return self._shape


Height = NewType("Height", int)
Width = NewType("Width", int)
Time = NewType("Time", int)
Batch = NewType("Batch", int)

v1: Array[Height, Width] = Array((Height(1), Width(2)))  # OK
v2: Array[Batch, Height, Width] = Array((Batch(1), Height(1), Width(1)))  # OK
v3: Array[Time, Batch, Height, Width] = Array(
    (Time(1), Batch(1), Height(1), Width(1))
)  # OK

v4: Array[Height, Width] = Array(Height(1))  # E
v5: Array[Batch, Height, Width] = Array((Batch(1), Width(1)))  # E
v6: Array[Time, Batch, Height, Width] = Array(  # E[v6]
    (Time(1), Batch(1), Width(1), Height(1))  # E[v6]
)


# > Type Variable Tuples Must Always be Unpacked


class ClassA(Generic[Shape]):  # E: not unpacked
    def __init__(self, shape: tuple[Shape]):  # E: not unpacked
        self._shape: tuple[*Shape] = shape

    def get_shape(self) -> tuple[Shape]:  # E: not unpacked
        return self._shape

    def method1(*args: Shape) -> None:  # E: not unpacked
        ...


# > TypeVarTuple does not yet support specification of variance, bounds, constraints.

Ts1 = TypeVarTuple("Ts1", covariant=True)  # E
Ts2 = TypeVarTuple("Ts2", int, float)  # E
Ts3 = TypeVarTuple("Ts3", bound=int)  # E


# > If the same TypeVarTuple instance is used in multiple places in a signature
# > or class, a valid type inference might be to bind the TypeVarTuple to a
# > tuple of a union of types.


def func2(arg1: tuple[*Ts], arg2: tuple[*Ts]) -> tuple[*Ts]:
    ...


# > We do not allow this; type unions may not appear within the tuple.
# > If a type variable tuple appears in multiple places in a signature,
# > the types must match exactly (the list of type parameters must be the
# > same length, and the type parameters themselves must be identical)

assert_type(func2((0,), (1,)), tuple[int])  # OK
func2((0,), (0.0,))  # OK
func2((0.0,), (0,))  # OK
func2((0,), (1,))  # OK

func2((0,), ("0",))  # E
func2((0, 0), (0,))  # E


def multiply(x: Array[*Shape], y: Array[*Shape]) -> Array[*Shape]:
    ...


def func3(x: Array[Height], y: Array[Width], z: Array[Height, Width]):
    multiply(x, x)  # OK
    multiply(x, y)  # E
    multiply(x, z)  # E


# > Only a single type variable tuple may appear in a type parameter list.


class Array3(Generic[*Ts1, *Ts2]):  # E
    ...
