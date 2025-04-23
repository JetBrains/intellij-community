"""
Tests for basic usage of the typing.Self type.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#self.

from typing import Callable, Generic, Self, TypeVar, assert_type

T = TypeVar("T")


class Shape:
    def set_scale(self, scale: float) -> Self:
        assert_type(self, Self)
        self.scale = scale
        return self

    def method2(self) -> Self:
        # This should result in a type error.
        return Shape()  # E

    def method3(self) -> "Shape":
        return self

    @classmethod
    def from_config(cls, config: dict[str, float]) -> Self:
        assert_type(cls, type[Self])
        return cls()

    @classmethod
    def cls_method2(cls) -> Self:
        # This should result in a type error.
        return Shape()  # E

    @classmethod
    def cls_method3(cls) -> "Shape":
        return cls()

    def difference(self, other: Self) -> float:
        assert_type(other, Self)
        return 0.0

    def apply(self, f: Callable[[Self], None]) -> None:
        return f(self)


class Circle(Shape):
    pass


assert_type(Shape().set_scale(1.0), Shape)
assert_type(Circle().set_scale(1.0), Circle)

assert_type(Shape.from_config({}), Shape)
assert_type(Circle.from_config({}), Circle)


class Container(Generic[T]):
    value: T

    def __init__(self, value: T) -> None:
        self.value = value

    def set_value(self, value: T) -> Self: ...

    # This should generate an error because Self isn't subscriptable.
    def foo(self, other: Self[int]) -> None:  # E
        pass


def object_with_concrete_type(
    int_container: Container[int], str_container: Container[str]
) -> None:
    assert_type(int_container.set_value(42), Container[int])
    assert_type(str_container.set_value("hello"), Container[str])


def object_with_generic_type(
    container: Container[T],
    value: T,
) -> Container[T]:
    val = container.set_value(value)
    assert_type(val, Container[T])
    return val
