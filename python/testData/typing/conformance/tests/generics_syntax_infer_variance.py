"""
Tests the handling of "infer_variance" parameter for TypeVar.
"""

# Specification: https://peps.python.org/pep-0695/#auto-variance-for-typevar

from typing import Final, Generic, Iterator, Sequence, TypeVar
from dataclasses import dataclass


T = TypeVar("T", infer_variance=True)
K = TypeVar("K", infer_variance=True)
V = TypeVar("V", infer_variance=True)

S1 = TypeVar("S1", covariant=True, infer_variance=True)  # E: cannot use covariant with infer_variance

S2 = TypeVar("S2", contravariant=True, infer_variance=True)  # E: cannot use contravariant with infer_variance


class ShouldBeCovariant1(Generic[T]):
    def __getitem__(self, index: int) -> T:
        ...

    def __iter__(self) -> Iterator[T]:
        ...


vco1_1: ShouldBeCovariant1[float] = ShouldBeCovariant1[int]()  # OK
vco1_2: ShouldBeCovariant1[int] = ShouldBeCovariant1[float]()  # E


class ShouldBeCovariant2(Sequence[T]):
    pass


vco2_1: ShouldBeCovariant2[float] = ShouldBeCovariant2[int]()  # OK
vco2_2: ShouldBeCovariant2[int] = ShouldBeCovariant2[float]()  # E


class ShouldBeCovariant3(Generic[T]):
    def method1(self) -> "ShouldBeCovariant2[T]":
        ...


vco3_1: ShouldBeCovariant3[float] = ShouldBeCovariant3[int]()  # OK
vco3_2: ShouldBeCovariant3[int] = ShouldBeCovariant3[float]()  # E


@dataclass(frozen=True)
class ShouldBeCovariant4(Generic[T]):
    x: T


# This test is problematic as of Python 3.13 because of the
# newly synthesized "__replace__" method, which causes the type
# variable to be inferred as invariant rather than covariant.
# See https://github.com/python/mypy/issues/17623#issuecomment-2266312738
# for details. Until we sort this out, we'll leave this test commented
# out.

# vo4_1: ShouldBeCovariant4[float] = ShouldBeCovariant4[int](1)  # OK
# vo4_4: ShouldBeCovariant4[int] = ShouldBeCovariant4[float](1.0)  # E


class ShouldBeCovariant5(Generic[T]):
    def __init__(self, x: T) -> None:
        self._x = x

    @property
    def x(self) -> T:
        return self._x


vo5_1: ShouldBeCovariant5[float] = ShouldBeCovariant5[int](1)  # OK
vo5_2: ShouldBeCovariant5[int] = ShouldBeCovariant5[float](1.0)  # E


class ShouldBeCovariant6(Generic[T]):
    x: Final[T]

    def __init__(self, value: T):
        self.x = value


vo6_1: ShouldBeCovariant6[float] = ShouldBeCovariant6[int](1)  # OK
vo6_2: ShouldBeCovariant6[int] = ShouldBeCovariant6[float](1.0)  # E


class ShouldBeInvariant1(Generic[T]):
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


class ShouldBeInvariant2(Generic[T]):
    def __init__(self, value: T) -> None:
        self._value = value

    def get_value(self) -> T:
        return self._value

    def set_value(self, value: T):
        self._value = value


vinv2_1: ShouldBeInvariant2[float] = ShouldBeInvariant2[int](1)  # E
vinv2_2: ShouldBeInvariant2[int] = ShouldBeInvariant2[float](1.1)  # E


class ShouldBeInvariant3(dict[K, V]):
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


class ShouldBeContravariant1(Generic[T]):
    def __init__(self, value: T) -> None:
        pass

    def set_value(self, value: T):
        pass


vcontra1_1: ShouldBeContravariant1[float] = ShouldBeContravariant1[int](1)  # E
vcontra1_2: ShouldBeContravariant1[int] = ShouldBeContravariant1[float](1.2)  # OK
