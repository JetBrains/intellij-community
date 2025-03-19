"""
Tests type variable variance inference for generic protocols.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/protocol.html#generic-protocols

from typing import ParamSpec, Protocol, TypeVar

T1 = TypeVar("T1")
T2 = TypeVar("T2", bound=int)
T3 = TypeVar("T3", bytes, str)
T1_co = TypeVar("T1_co", covariant=True)
T1_contra = TypeVar("T1_contra", contravariant=True)
P = ParamSpec("P")
R = TypeVar("R", covariant=True)

# > Type checkers will warn if the inferred variance is different from the
# > declared variance.


class AnotherBox(Protocol[T1]):  # E: T should be covariant
    def content(self) -> T1:
        ...


class Protocol1(Protocol[T1, T2, T3]):  # OK
    def m1(self, p0: T1, p1: T2, p2: T3) -> T1 | T2:
        ...

    def m2(self) -> T1:
        ...

    def m3(self) -> T2:
        ...

    def m4(self) -> T3:
        ...


class Protocol2(Protocol[T1, T2, T3]):  # E: T3 should be contravariant
    def m1(self, p0: T1, p1: T2, p2: T3) -> T1:
        ...

    def m2(self) -> T1:
        ...

    def m3(self) -> T2:
        ...


class Protocol3(Protocol[T1_co]):
    def m1(self) -> None:
        pass


class Protocol4(Protocol[T1]):  # E: T1 should be contravariant
    def m1(self, p0: T1) -> None:
        ...


class Protocol5(Protocol[T1_co]):  # E: T1_co should be contravariant
    def m1(self, p0: T1_co) -> None:  # E?: Incorrect use of covariant TypeVar
        ...


class Protocol6(Protocol[T1]):  # E: T1 should be covariant
    def m1(self) -> T1:
        ...


class Protocol7(Protocol[T1_contra]):  # E: T1_contra should be covariant
    def m1(self) -> T1_contra:  # E?: Incorrect use of contravariant TypeVar
        ...


class Protocol8(Protocol[T1]):  # OK
    def m1(self) -> T1:
        ...

    def m2(self, p1: T1) -> None:
        pass


class Callback(Protocol[P, R]):  # OK
    def __call__(self, *args: P.args, **kwargs: P.kwargs) -> R:
        ...


class Protocol9(Protocol[T1_co]):  # OK
    @property
    def prop1(self) -> T1_co:
        ...


class Protocol10(Protocol[T1_co]):  # OK
    def m1(self) -> type[T1_co]:
        ...


class Protocol11(Protocol[T1]):  # OK
    x: T1 | None


class Protocol12(Protocol[T1]):  # E: T1 should be covariant
    # __init__ method is exempt from variance calculations.
    def __init__(self, x: T1) -> None:
        ...


class Protocol13(Protocol[T1_contra]):  # OK
    def m1(self: "Protocol13[T1_contra]", x: T1_contra) -> None:
        ...

    @classmethod
    def m2(cls: "type[Protocol13[T1_contra]]") -> None:
        ...


class Protocol14(Protocol[T1]):  # OK
    def m1(self) -> list[T1]:
        ...
