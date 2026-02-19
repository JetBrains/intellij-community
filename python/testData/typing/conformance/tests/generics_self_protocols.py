"""
Tests for usage of the typing.Self type with protocols.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#use-in-protocols

from typing import Protocol, Self, assert_type


class ShapeProtocol(Protocol):
    def set_scale(self, scale: float) -> Self:
        ...


class ReturnSelf:
    scale: float = 1.0

    def set_scale(self, scale: float) -> Self:
        self.scale = scale
        return self


class ReturnConcreteShape:
    scale: float = 1.0

    def set_scale(self, scale: float) -> "ReturnConcreteShape":
        self.scale = scale
        return self


class BadReturnType:
    scale: float = 1.0

    def set_scale(self, scale: float) -> int:
        self.scale = scale
        return 42


class ReturnDifferentClass:
    scale: float = 1.0

    def set_scale(self, scale: float) -> ReturnConcreteShape:
        return ReturnConcreteShape()


def accepts_shape(shape: ShapeProtocol) -> None:
    y = shape.set_scale(0.5)
    assert_type(y, ShapeProtocol)


def main(
    return_self_shape: ReturnSelf,
    return_concrete_shape: ReturnConcreteShape,
    bad_return_type: BadReturnType,
    return_different_class: ReturnDifferentClass,
) -> None:
    accepts_shape(return_self_shape)  # OK
    accepts_shape(return_concrete_shape)  # OK

    # This should generate a type error.
    accepts_shape(bad_return_type)  # E

    # Not OK because it returns a non-subclass.
    accepts_shape(return_different_class)  # E
