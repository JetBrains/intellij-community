"""
Tests variance inference for type parameters.
"""

# Specification: https://peps.python.org/pep-0695/#variance-inference

from dataclasses import dataclass

# T1 should be invariant
# T2 should be contravariant
# T3 should be covariant
from typing import Generic, Iterator, Sequence, TypeVar


class ClassA[T1, T2, T3](list[T1]):
    def method1(self, a: T2) -> None:
        ...

    def method2(self) -> T3:
        ...


def func_a(p1: ClassA[float, int, int], p2: ClassA[int, float, float]):
    v1: ClassA[int, int, int] = p1  # E
    v2: ClassA[float, float, int] = p1  # E
    v3: ClassA[float, int, float] = p1  # OK

    v4: ClassA[int, int, int] = p2  # E
    v5: ClassA[int, int, float] = p2  # OK


class ShouldBeCovariant1[T]:
    def __getitem__(self, index: int) -> T:
        ...

    def __iter__(self) -> Iterator[T]:
        ...


vco1_1: ShouldBeCovariant1[float] = ShouldBeCovariant1[int]()  # OK
vco1_2: ShouldBeCovariant1[int] = ShouldBeCovariant1[float]()  # E


class ShouldBeCovariant2[T](ShouldBeCovariant1[T]):
    pass


vco2_1: ShouldBeCovariant2[float] = ShouldBeCovariant2[int]()  # OK
vco2_2: ShouldBeCovariant2[int] = ShouldBeCovariant2[float]()  # E


class ShouldBeCovariant3[T]:
    def method1(self) -> "ShouldBeCovariant2[T]":
        ...


vco3_1: ShouldBeCovariant3[float] = ShouldBeCovariant3[int]()  # OK
vco3_2: ShouldBeCovariant3[int] = ShouldBeCovariant3[float]()  # E


@dataclass(frozen=True)
class ShouldBeCovariant4[T]:
    x: T


vo4_1: ShouldBeCovariant4[float] = ShouldBeCovariant4[int](1)  # OK
vo4_2: ShouldBeCovariant4[int] = ShouldBeCovariant4[float](1)  # E


class ShouldBeCovariant5[T]:
    def __init__(self, x: T) -> None:
        self._x = x

    @property
    def x(self) -> T:
        return self._x


vo5_1: ShouldBeCovariant5[float] = ShouldBeCovariant5[int](1)  # OK
vo5_2: ShouldBeCovariant5[int] = ShouldBeCovariant5[float](1)  # E


class ShouldBeInvariant1[T]:
    def __init__(self, value: T) -> None:
        self._value = value

    @property
    def value(self) -> T:
        return self._value

    @value.setter
    def value(self, value: T):
        self._value = value


vinv1_1: ShouldBeInvariant1[float] = ShouldBeInvariant1[int](1)  # E
vinv1_2: ShouldBeInvariant1[int] = ShouldBeInvariant1[float](1.1)  # E


class ShouldBeInvariant2[T]:
    def __init__(self, value: T) -> None:
        self._value = value

    def get_value(self) -> T:
        return self._value

    def set_value(self, value: T):
        self._value = value


vinv2_1: ShouldBeInvariant2[float] = ShouldBeInvariant2[int](1)  # E
vinv2_2: ShouldBeInvariant2[int] = ShouldBeInvariant2[float](1.1)  # E


class ShouldBeInvariant3[K, V](dict[K, V]):
    pass


vinv3_1: ShouldBeInvariant3[float, str] = ShouldBeInvariant3[int, str]()  # E
vinv3_2: ShouldBeInvariant3[int, str] = ShouldBeInvariant3[float, str]()  # E
vinv3_3: ShouldBeInvariant3[str, float] = ShouldBeInvariant3[str, int]()  # E
vinv3_4: ShouldBeInvariant3[str, int] = ShouldBeInvariant3[str, float]()  # E


@dataclass
class ShouldBeInvariant4[T]:
    x: T


vinv4_1: ShouldBeInvariant4[float] = ShouldBeInvariant4[int](1)  # E


class ShouldBeInvariant5[T]:
    def __init__(self, x: T) -> None:
        self.x = x


vinv5_1: ShouldBeInvariant5[float] = ShouldBeInvariant5[int](1)  # E


class ShouldBeContravariant1[T]:
    def __init__(self, value: T) -> None:
        pass

    def set_value(self, value: T) -> None:
        pass


vcontra1_1: ShouldBeContravariant1[float] = ShouldBeContravariant1[int](1)  # E
vcontra1_2: ShouldBeContravariant1[int] = ShouldBeContravariant1[float](1.2)  # OK


# Test the case where a class with inferred variance derives from
# a traditional class that doesn't use inferred variance.

T = TypeVar("T")
T_co = TypeVar("T_co", covariant=True)
T_contra = TypeVar("T_contra", contravariant=True)


class Parent_Invariant(Generic[T]):
    pass


class ShouldBeInvariant6[T](Parent_Invariant[T]):
    pass


a1: ShouldBeInvariant6[int] = ShouldBeInvariant6[float]()  # E
a2: ShouldBeInvariant6[float] = ShouldBeInvariant6[int]()  # E


class Parent_Covariant(Generic[T_co]):
    pass


class ShouldBeCovariant6[T](Parent_Covariant[T]):
    pass


b1: ShouldBeCovariant6[int] = ShouldBeCovariant6[float]()  # E
b2: ShouldBeCovariant6[float] = ShouldBeCovariant6[int]()  # OK


class Parent_Contravariant(Generic[T_contra]):
    pass


class ShouldBeContravariant2[T](Parent_Contravariant[T]):
    pass


c1: ShouldBeContravariant2[int] = ShouldBeContravariant2[float]()  # OK
c2: ShouldBeContravariant2[float] = ShouldBeContravariant2[int]()  # E
