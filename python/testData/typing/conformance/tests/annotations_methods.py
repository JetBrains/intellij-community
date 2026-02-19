"""
Tests for annotating instance and class methods.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/annotations.html#annotating-instance-and-class-methods

from typing import TypeVar, assert_type


T = TypeVar("T", bound="A")


class A:
    def copy(self: T) -> T:
        return self

    @classmethod
    def factory(cls: type[T]) -> T:
        return cls()

    @staticmethod
    def static_method(val: type[T]) -> T:
        return val()


class B(A):
    ...


assert_type(A().copy(), A)
assert_type(A.factory(), A)
assert_type(A.copy(A()), A)
assert_type(B.copy(B()), B)

assert_type(B().copy(), B)
assert_type(B.factory(), B)

# This case is ambiguous in the spec, which does not indicate when
# type binding should be performed. Currently, pyright evaluates
# A here, but mypy evaluates B. Since the spec is not clear, both
# of these are currently acceptable answers.
assert_type(A.copy(B()), A)  # E?

# Similarly, this case is ambiguous in the spec. Pyright currently
# generates a type error here, but mypy accepts this.
B.copy(A())  # E?

assert_type(A.static_method(A), A)
assert_type(A.static_method(B), B)
assert_type(B.static_method(B), B)
assert_type(B.static_method(A), A)
