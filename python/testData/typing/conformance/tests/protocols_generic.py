"""
Tests the handling of generic protocols.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/protocol.html#generic-protocols


from typing import Callable, Generic, Iterator, Protocol, Self, TypeVar, assert_type

S = TypeVar("S")
T = TypeVar("T")
T_co = TypeVar("T_co", covariant=True)
T_contra = TypeVar("T_contra", contravariant=True)


class Iterable(Protocol[T_co]):
    def __iter__(self) -> Iterator[T_co]:
        ...


# > Protocol[T, S, ...] is allowed as a shorthand for Protocol, Generic[T, S, ...].
# In particular, the implicit order of type parameters is dictated by
# the order in which they appear in the `Protocol` subscript.


class Proto1(Iterable[T_co], Protocol[S, T_co]):
    def method1(self, x: S) -> S:
        ...


class Concrete1:
    def __iter__(self) -> Iterator[int]:
        return (x for x in [1, 2, 3])

    def method1(self, x: str) -> str:
        return ""


p1: Proto1[str, int] = Concrete1()  # OK
p2: Proto1[int, str] = Concrete1()  # E: incompatible type


# Runtime error: Protocol and Generic cannot be used together as base classes.
class Proto2(Protocol[T_co], Generic[T_co]):  # E
    ...


# > User-defined generic protocols support explicitly declared variance.
class Box(Protocol[T_co]):
    def content(self) -> T_co:
        ...


def func1(box_int: Box[int], box_float: Box[float]):
    v1: Box[float] = box_int  # OK
    v2: Box[int] = box_float  # E


class Sender(Protocol[T_contra]):
    def send(self, data: T_contra) -> int:
        return 0


def func2(sender_int: Sender[int], sender_float: Sender[float]):
    v1: Sender[int] = sender_float  # OK
    v2: Sender[float] = sender_int  # E


class AttrProto(Protocol[T]):
    attr: T


def func3(attr_int: AttrProto[int], attr_float: AttrProto[float]):
    v1: AttrProto[float] = attr_int  # E
    v2: AttrProto[int] = attr_float  # E


class HasParent(Protocol):
    def get_parent(self: T) -> T:
        ...


GenericHasParent = TypeVar("GenericHasParent", bound=HasParent)


def generic_get_parent(n: GenericHasParent) -> GenericHasParent:
    return n.get_parent()


class ConcreteHasParent:
    def get_parent(self) -> Self:
        return self


parent = generic_get_parent(ConcreteHasParent())  # OK
assert_type(parent, ConcreteHasParent)


class HasPropertyProto(Protocol):
    @property
    def f(self: T) -> T:
        ...

    def m(self, item: T, callback: Callable[[T], str]) -> str:
        ...


class ConcreteHasProperty1:
    @property
    def f(self: T) -> T:
        return self

    def m(self, item: T, callback: Callable[[T], str]) -> str:
        return ""


class ConcreteHasProperty2:
    @property
    def f(self) -> Self:
        return self

    def m(self, item: int, callback: Callable[[int], str]) -> str:
        return ""


class ConcreteHasProperty3:
    @property
    def f(self) -> int:
        return 0

    def m(self, item: int, callback: Callable[[int], str]) -> str:
        return ""


class ConcreteHasProperty4:
    @property
    def f(self) -> Self:
        return self

    def m(self, item: str, callback: Callable[[int], str]) -> str:
        return ""


hp1: HasPropertyProto = ConcreteHasProperty1()  # OK
hp2: HasPropertyProto = ConcreteHasProperty2()  # E
hp3: HasPropertyProto = ConcreteHasProperty3()  # E
hp4: HasPropertyProto = ConcreteHasProperty4()  # E
