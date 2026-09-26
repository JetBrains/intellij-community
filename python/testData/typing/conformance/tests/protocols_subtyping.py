"""
Tests subtyping rules for protocols.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/protocol.html#subtyping-relationships-with-other-types

# > Protocols cannot be instantiated.

from typing import Hashable, Iterable, Protocol, Sequence, TypeVar


class Proto1(Protocol):
    pass


p1 = Proto1()  # E: protocol cannot be instantiated


# > A protocol is never a subtype of a concrete type.

# > A concrete type X is a subtype of protocol P if and only if X implements
# > all protocol members of P with compatible types. In other words, subtyping
# > with respect to a protocol is always structural.


class Proto2(Protocol):
    def method1(self) -> None:
        ...


class Concrete2:
    def method1(self) -> None:
        pass


def func1(p2: Proto2, c2: Concrete2):
    v1: Proto2 = c2  # OK
    v2: Concrete2 = p2  # E


# > A protocol P1 is a subtype of another protocol P2 if P1 defines all
# > protocol members of P2 with compatible types.


class Proto3(Protocol):
    def method1(self) -> None:
        ...

    def method2(self) -> None:
        ...


def func2(p2: Proto2, p3: Proto3):
    v1: Proto2 = p3  # OK
    v2: Proto3 = p2  # E


# > Generic protocols follow the rules for generic abstract classes, except
# > for using structural compatibility instead of compatibility defined by
# > inheritance relationships.

S = TypeVar("S")
T = TypeVar("T")


class Proto4(Protocol[S, T]):
    def method1(self, a: S, b: T) -> tuple[S, T]:
        ...


class Proto5(Protocol[T]):
    def method1(self, a: T, b: T) -> tuple[T, T]:
        ...


def func3(p4_int: Proto4[int, int], p5_int: Proto5[int]):
    v1: Proto4[int, int] = p5_int  # OK
    v2: Proto5[int] = p4_int  # OK
    v3: Proto4[int, float] = p5_int  # E
    v4: Proto5[float] = p4_int  # E


S_co = TypeVar("S_co", covariant=True)
T_contra = TypeVar("T_contra", contravariant=True)


class Proto6(Protocol[S_co, T_contra]):
    def method1(self, a: T_contra) -> Sequence[S_co]:
        ...


class Proto7(Protocol[S_co, T_contra]):
    def method1(self, a: T_contra) -> Sequence[S_co]:
        ...


def func4(p6: Proto6[float, float]):
    v1: Proto7[object, int] = p6  # OK
    v2: Proto7[float, float] = p6  # OK
    v3: Proto7[complex, int] = p6  # OK

    v4: Proto7[int, float] = p6  # E
    v5: Proto7[float, object] = p6  # E


# > Unions of protocol classes behaves the same way as for non-protocol classes.


class SupportsExit(Protocol):
    def exit(self) -> int:
        ...


class SupportsQuit(Protocol):
    def quit(self) -> int | None:
        ...


def finish(task: SupportsExit | SupportsQuit) -> int:
    return 0


class DefaultJob:
    def quit(self) -> int:
        return 0


finish(DefaultJob())  # OK


# > One can use multiple inheritance to define an intersection of protocols.


class HashableFloats(Iterable[float], Hashable, Protocol):
    pass


def cached_func(args: HashableFloats) -> float:
    return 0.0


cached_func((1, 2, 3))  # OK, tuple is both hashable and iterable
