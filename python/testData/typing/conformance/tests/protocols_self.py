"""
Tests the handling of annotated "self" parameters in a protocol.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/protocol.html#self-types-in-protocols

from typing import Generic, Protocol, Self, TypeVar


C = TypeVar("C", bound="Copyable")


class Copyable(Protocol):
    def copy(self: C) -> C:
        return self


class One:
    def copy(self) -> "One":
        return One()


T = TypeVar("T", bound="Other")


class Other:
    def copy(self: T) -> T:
        return self


c: Copyable
c = One()  # OK
c = Other()  # OK


T1_co = TypeVar("T1_co", covariant=True)
T2_co = TypeVar("T2_co", covariant=True)


class P1Parent(Protocol[T2_co]):
    def f0(self, /) -> Self:
        ...


class P1Child(P1Parent[T2_co], Protocol[T2_co]):
    ...


class C1(Generic[T1_co]):
    def f0(self, /) -> Self:
        return self


a1: P1Parent[str] = C1[str]()
b1: P1Child[str] = C1[str]()


class P2Parent(Protocol[T1_co]):
    def f0(self, right: Self, /) -> "P2Parent[T1_co]":
        return right


class P2Child(P2Parent[T1_co], Protocol[T1_co]):
    ...


class C2(Generic[T2_co]):
    def f0(self, other: Self) -> "C2[T2_co]":
        return other


a2: P2Parent[str] = C2[str]()  # OK
b2: P2Child[str] = C2[str]()  # OK
