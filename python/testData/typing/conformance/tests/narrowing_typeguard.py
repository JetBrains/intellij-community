"""
Tests TypeGuard functionality.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/narrowing.html#typeguard

from typing import Any, Callable, Protocol, Self, TypeGuard, TypeVar, assert_type


T = TypeVar("T")

def is_two_element_tuple(val: tuple[T, ...]) -> TypeGuard[tuple[T, T]]:
    return len(val) == 2

def func1(names: tuple[str, ...]):
    if is_two_element_tuple(names):
        assert_type(names, tuple[str, str])
    else:
        assert_type(names, tuple[str, ...])


def is_str_list(val: list[object], allow_empty: bool) -> TypeGuard[list[str]]:
    if len(val) == 0:
        return allow_empty
    return all(isinstance(x, str) for x in val)

def is_set_of(val: set[Any], type: type[T]) -> TypeGuard[set[T]]:
    return all(isinstance(x, type) for x in val)

def func2(val: set[object]):
    if is_set_of(val, int):
        assert_type(val, set[int])
    else:
        assert_type(val, set[object])


T_A = TypeVar("T_A", bound="A")

class A:
    def tg_1(self, val: object) -> TypeGuard[int]:
        return isinstance(val, int)

    @classmethod
    def tg_2(cls, val: object) -> TypeGuard[int]:
        return isinstance(val, int)

    @staticmethod
    def tg_3(val: object) -> TypeGuard[int]:
        return isinstance(val, int)

    def tg4(self, val: object) -> TypeGuard[Self]:
        return isinstance(val, type(self))

    def tg5(self: T_A, val: object) -> TypeGuard[T_A]:
        return isinstance(val, type(self))

class B(A):
    pass

# > Type checkers should assume that type narrowing should be applied to
# > the expression that is passed as the first positional argument to a
# > user-defined type guard. If the type guard function accepts more than
# > one argument, no type narrowing is applied to those additional argument
# > expressions.

def func3() -> None:
    val1 = object()
    if A().tg_1(val1):
        assert_type(val1, int)

    val2 = object()
    if A().tg_2(val2):
        assert_type(val2, int)

    val3 = object()
    if A.tg_2(val3):
        assert_type(val3, int)

    val4 = object()
    if A().tg_3(val4):
        assert_type(val4, int)

    val5 = object()
    if A.tg_3(val5):
        assert_type(val5, int)

    val6 = object()
    if B().tg4(val6):
        assert_type(val6, B)

    val7 = object()
    if B().tg4(val7):
        assert_type(val7, B)


# > If a type guard function is implemented as an instance method or class
# > method, the first positional argument maps to the second parameter
# > (after “self” or “cls”).

class C:
    # Type checker should emit error here.
    def tg_1(self) -> TypeGuard[int]:  # E
        return False

    @classmethod
    # Type checker should emit error here.
    def tg_2(cls) -> TypeGuard[int]:  # E
        return False

# > ``TypeGuard`` is also valid as the return type of a ``Callable`` type. In that
# > context, it is treated as a subtype of bool. For example, ``Callable[..., TypeGuard[int]]``
# > is assignable to ``Callable[..., bool]``.


def takes_callable_bool(f: Callable[[object], bool]) -> None:
    pass


def takes_callable_str(f: Callable[[object], str]) -> None:
    pass


def simple_typeguard(val: object) -> TypeGuard[int]:
    return isinstance(val, int)


takes_callable_bool(simple_typeguard)  # OK
takes_callable_str(simple_typeguard)   # E


class CallableBoolProto(Protocol):
    def __call__(self, val: object) -> bool: ...


class CallableStrProto(Protocol):
    def __call__(self, val: object) -> str: ...


def takes_callable_bool_proto(f: CallableBoolProto) -> None:
    pass


def takes_callable_str_proto(f: CallableStrProto) -> None:
    pass


takes_callable_bool_proto(simple_typeguard)  # OK
takes_callable_str_proto(simple_typeguard)   # E

# > Unlike ``TypeGuard``, ``TypeIs`` is invariant in its argument type:
# > ``TypeIs[B]`` is not a subtype of ``TypeIs[A]``,
# > even if ``B`` is a subtype of ``A``.

def takes_int_typeguard(f: Callable[[object], TypeGuard[int]]) -> None:
    pass


def int_typeguard(val: object) -> TypeGuard[int]:
    return isinstance(val, int)


def bool_typeguard(val: object) -> TypeGuard[bool]:
    return isinstance(val, bool)


takes_int_typeguard(int_typeguard)  # OK
takes_int_typeguard(bool_typeguard)  # OK
