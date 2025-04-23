"""
Tests valid/invalid definition of overloaded functions in stub files, where the
rules differ from non-stubs, since an implementation is not required.
"""

from typing import (
    final,
    overload,
    override,
)

# > At least two @overload-decorated definitions must be present.
@overload  # E[func1]
def func1() -> None:  # E[func1]: At least two overloads must be present
    ...

# > The ``@overload``-decorated definitions must be followed by an overload
# > implementation, which does not include an ``@overload`` decorator. Type
# > checkers should report an error or warning if an implementation is missing.
# > Overload definitions within stub files, protocols, and on abstract methods
# > within abstract base classes are exempt from this check.
@overload
def func2(x: int) -> int: ...
@overload
def func2(x: str) -> str: ...

# > If one overload signature is decorated with ``@staticmethod`` or
# > ``@classmethod``, all overload signatures must be similarly decorated. The
# > implementation, if present, must also have a consistent decorator. Type
# > checkers should report an error if these conditions are not met.
class C:
    @overload  # E[func5]
    def func5(self, x: int, /) -> int:  # E[func5]
        ...
    @overload
    @staticmethod
    def func5(x: str, /) -> str:  # E[func5]
        ...
    @overload  # E[func6]
    @classmethod
    def func6(cls, x: int, /) -> int:  # E[func6]
        ...
    @overload
    def func6(self, *args: str) -> str:  # E[func6]
        ...

# > If a ``@final`` or ``@override`` decorator is supplied for a function with
# > overloads, the decorator should be applied only to the overload
# > implementation if it is present. If an overload implementation isn't present
# > (for example, in a stub file), the ``@final`` or ``@override`` decorator
# > should be applied only to the first overload. Type checkers should enforce
# > these rules and generate an error when they are violated. If a ``@final`` or
# > ``@override`` decorator follows these rules, a type checker should treat the
# > decorator as if it is present on all overloads.
class Base:
    # This is a good definition of an overloaded final method in a stub (@final
    # decorator on first overload only):

    @overload
    @final
    def final_method(self, x: int) -> int: ...
    @overload
    def final_method(self, x: str) -> str: ...

    # The @final decorator should not be on multiple overloads:

    @overload  # E[invalid_final] @final should be on first overload
    @final
    def invalid_final(self, x: int) -> int:  # E[invalid_final]
        ...
    @overload  # E[invalid_final]
    @final
    def invalid_final(self, x: str) -> str:  # E[invalid_final]
        ...
    @overload
    def invalid_final(self, x: bytes) -> bytes: ...

    # The @final decorator should not be on all overloads:

    @overload  # E[invalid_final_2] @final should be on first overload
    @final
    def invalid_final_2(self, x: int) -> int:  # E[invalid_final_2]
        ...
    @overload  # E[invalid_final_2]
    @final
    def invalid_final_2(self, x: str) -> str: ...  # E[invalid_final_2]

    # These methods are just here for the @override test below. We use an
    # overload because mypy doesn't like overriding a non-overloaded method
    # with an overloaded one, even if LSP isn't violated. That could be its own
    # specification question, but it's not what we're trying to test here:

    @overload
    def good_override(self, x: int) -> int: ...
    @overload
    def good_override(self, x: str) -> str: ...
    @overload
    def to_override(self, x: int) -> int: ...
    @overload
    def to_override(self, x: str) -> str: ...

class Child(Base):  # E[override-final]
    # The correctly-decorated @final method `Base.final_method` should cause an
    # error if overridden in a child class (we use an overload here to avoid
    # questions of override LSP compatibility and focus only on the override):

    @overload  # E[override-final]
    def final_method(self, x: int) -> int:  # E[override-final]
        ...
    @overload
    def final_method(  # E[override-final] can't override final method
        self, x: str
    ) -> str:  # E[override-final] can't override final method
        ...

    # This is the right way to mark an overload as @override (decorate first
    # overload only), so the use of @override should cause an error (because
    # there's no `Base.bad_override` method):

    @overload  # E[bad_override] marked as override but doesn't exist in base
    @override
    def bad_override(self, x: int) -> int:  # E[bad_override]
        ...
    @overload
    def bad_override(self, x: str) -> str: ...

    # This is also a correctly-decorated overloaded @override, which is
    # overriding a method that does exist in the base, so there should be no
    # error. We need both this test and the previous one, because in the
    # previous test, an incorrect error about the use of @override decorator
    # could appear on the same line as the expected error about overriding a
    # method that doesn't exist in base:

    @overload
    @override
    def good_override(self, x: int) -> int: ...
    @overload
    def good_override(self, x: str) -> str: ...

    # This is the wrong way to use @override with an overloaded method, and
    # should emit an error:

    @overload  # E[override_impl]: @override should appear only on first overload
    def to_override(self, x: int) -> int: ...
    @overload
    @override
    def to_override(  # E[override_impl]: @override should appear only on first overload
        self, x: str
    ) -> str:  # E[override_impl]: @override should appear only on first overload
        ...
