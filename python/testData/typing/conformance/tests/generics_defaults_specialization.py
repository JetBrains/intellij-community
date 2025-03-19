"""
Tests for specialization rules associated with type parameters with
default values.
"""

# > A generic type alias can be further subscripted following normal subscription
# > rules. If a type parameter has a default that hasn't been overridden, it should
# > be treated like it was substituted into the type alias.

from typing import Generic, TypeAlias, assert_type
from typing_extensions import TypeVar

T1 = TypeVar("T1")
T2 = TypeVar("T2")
DefaultIntT = TypeVar("DefaultIntT", default=int)
DefaultStrT = TypeVar("DefaultStrT", default=str)


class SomethingWithNoDefaults(Generic[T1, T2]): ...


MyAlias: TypeAlias = SomethingWithNoDefaults[int, DefaultStrT]  # OK


def func1(p1: MyAlias, p2: MyAlias[bool]):
    assert_type(p1, SomethingWithNoDefaults[int, str])
    assert_type(p2, SomethingWithNoDefaults[int, bool])


MyAlias[bool, int]  # E: too many arguments passed to MyAlias


# > Generic classes with type parameters that have defaults behave similarly
# > generic type aliases. That is, subclasses can be further subscripted following
# > normal subscription rules, non-overridden defaults should be substituted.


class SubclassMe(Generic[T1, DefaultStrT]):
    x: DefaultStrT


class Bar(SubclassMe[int, DefaultStrT]): ...


assert_type(Bar, type[Bar[str]])
assert_type(Bar(), Bar[str])
assert_type(Bar[bool](), Bar[bool])


class Foo(SubclassMe[float]): ...


assert_type(Foo().x, str)

Foo[str]  # E: Foo cannot be further subscripted


class Baz(Generic[DefaultIntT, DefaultStrT]): ...


class Spam(Baz): ...


# Spam is <subclass of Baz[int, str]>
v1: Baz[int, str] = Spam()
