"""
Tests for valid and invalid usage of the typing.Self type.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#valid-locations-for-self

from typing import Any, Callable, Generic, Self, TypeAlias, TypeVar

class ReturnsSelf:
    def foo(self) -> Self: # Accepted
        return self

    @classmethod
    def bar(cls) -> Self:  # Accepted
        return cls(1)

    def __new__(cls, value: int) -> Self:  # Accepted
        return cls(1)

    def explicitly_use_self(self: Self) -> Self:  # Accepted
        return self

    # Accepted (Self can be nested within other types)
    def returns_list(self) -> list[Self]:
        return []

    # Accepted (Self can be nested within other types)
    @classmethod
    def return_cls(cls) -> type[Self]:
        return cls

class Child(ReturnsSelf):
    # Accepted (we can override a method that uses Self annotations)
    def foo(self) -> Self:
        return self

class TakesSelf:
    def foo(self, other: Self) -> bool:  # Accepted
        return True

class Recursive:
    # Accepted (treated as an @property returning ``Self | None``)
    next: Self | None

class CallableAttribute:
    def foo(self) -> int:
        return 0

    # Accepted (treated as an @property returning the Callable type)
    bar: Callable[[Self], int] = foo

class HasNestedFunction:
    x: int = 42

    def foo(self) -> None:

        # Accepted (Self is bound to HasNestedFunction).
        def nested(z: int, inner_self: Self) -> Self:
            print(z)
            print(inner_self.x)
            return inner_self

        nested(42, self)  # OK


class Outer:
    class Inner:
        def foo(self) -> Self:  # Accepted (Self is bound to Inner)
            return self


# This should generate an error.
def foo(bar: Self) -> Self: ...  # E: not within a class

# This should generate an error.
bar: Self  # E: not within a class

TFoo2 = TypeVar("TFoo2", bound="Foo2")

class Foo2:
    # Rejected (Self is treated as unknown).
    def has_existing_self_annotation(self: TFoo2) -> Self: ... # E

class Foo3:
    def return_concrete_type(self) -> Self:
        return Foo3()  # E (see FooChild below for rationale)

class Foo3Child(Foo3):
    child_value: int = 42

    def child_method(self) -> None:
        y = self.return_concrete_type()
        y.child_value

T = TypeVar("T")

class Bar(Generic[T]):
    def bar(self) -> T: ...

# This should generate an error.
class Baz(Bar[Self]): ...  # E

class Baz2(Self): ...  # E

# This should generate an error.
TupleSelf: TypeAlias = tuple[Self]  # E

class Base:
    @staticmethod
    # This should generate an error.
    def make() -> Self:  # E
        ...

    @staticmethod
    # This should generate an error.
    def return_parameter(foo: Self) -> Self:  # E
        ...

class MyMetaclass(type):
    # This should generate an error.
    def __new__(cls, *args: Any) -> Self:  # E
        ...

    # This should generate an error.
    def __mul__(cls, count: int) -> list[Self]:  # E
        ...
