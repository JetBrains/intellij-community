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


# > It is an error to combine the shorthand with Generic[T, S, ...]
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


# Specification: https://typing.readthedocs.io/en/latest/spec/protocol.html#self-types-in-protocols
# > The self-types in protocols follow the rules for other methods.
# Specification: https://typing.readthedocs.io/en/latest/spec/annotations.html#annotating-instance-and-class-methods
# > In addition, the first argument in an instance method can be annotated
# > with a type variable. In this case the return type may use the same
# > type variable, thus making that method a generic function.


T_bounded = TypeVar("T_bounded", bound="HasParent")


class HasParent(Protocol):
    def get_parent(self: T_bounded) -> T_bounded:
        ...


GenericHasParent = TypeVar("GenericHasParent", bound=HasParent)


def generic_get_parent(n: GenericHasParent) -> GenericHasParent:
    return n.get_parent()


class ConcreteHasParent:
    def get_parent(self) -> Self:
        return self


parent = generic_get_parent(ConcreteHasParent())  # OK
assert_type(parent, ConcreteHasParent)


# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#use-in-protocols
# > `Self` is valid within Protocols, similar to its use in classes:


class HasParentProperty(Protocol):
    @property
    def parent(self) -> Self:
        ...


class ConcreteParentProperty:
    @property
    def parent(self) -> Self:
        return self


class InvalidParentProperty:
    @property
    def parent(self) -> HasParentProperty:
        return ConcreteParentProperty()


hp1: HasParentProperty = ConcreteParentProperty()  # OK
hp2: HasParentProperty = InvalidParentProperty()  # E



class HasMethod(Protocol):
    def m(self, item: T, callback: Callable[[T], str]) -> str:
        ...


class ConcreteHasMethod:
    def m(self, item: T, callback: Callable[[T], str]) -> str:
        return ""


class InvalidHasMethod:
    def m(self, item: int, callback: Callable[[int], str]) -> str:
        return ""


hm1: HasMethod = ConcreteHasMethod()  # OK
hm2: HasMethod = InvalidHasMethod()  # E


# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#use-in-protocols
# > Checking a class for assignability to a protocol: If a protocol uses `Self`
# > in methods or attribute annotations, then a class `Foo` is assignable
# > to the protocol if its corresponding methods and attribute annotations use
# > either `Self` or `Foo` or any of `Foo`'s subclasses.


class HasGreaterThan(Protocol):
    def __gt__(self, other: Self) -> bool:
        ...


class ConcreteGreaterThan1:
    def __gt__(self, other: Self) -> bool:
        return False


class ConcreteGreaterThan2:
    def __gt__(self, other: "ConcreteGreaterThan2") -> bool:
        return False


class InvalidGreaterThan:
    def __gt__(self, other: int) -> bool:
        return False


hg1: HasGreaterThan = ConcreteGreaterThan1()  # OK
hg2: HasGreaterThan = ConcreteGreaterThan2()  # OK
hg3: HasGreaterThan = InvalidGreaterThan()  # E
