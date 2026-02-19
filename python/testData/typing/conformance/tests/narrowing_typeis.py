"""
Tests TypeIs functionality.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/narrowing.html#typeis

from collections.abc import Awaitable
from typing import Any, Callable, Protocol, Self, TypeGuard, TypeVar, assert_type
from typing_extensions import TypeIs


T = TypeVar("T")

def is_two_element_tuple(val: tuple[T, ...]) -> TypeIs[tuple[T, T]]:
    return len(val) == 2

def func1(names: tuple[str, ...]):
    if is_two_element_tuple(names):
        assert_type(names, tuple[str, str])
    else:
        assert_type(names, tuple[str, ...])


# > The final narrowed type may be narrower than **R**, due to the constraints of the
# > argument's previously-known type

def is_awaitable(val: object) -> TypeIs[Awaitable[Any]]:
    return isinstance(val, Awaitable)

async def func2(val: int | Awaitable[int]):
    if is_awaitable(val):
        # Not `assert_type(val, Awaitable[int])` because a conformant
        # implementation could allow the possibility that instead it is
        # `int & Awaitable[Any]`, an awaitable subclass of int.
        x: int = await val
        return x
    else:
        assert_type(val, int)


T_A = TypeVar("T_A", bound="A")

class A:
    def tg_1(self, val: object) -> TypeIs[int]:
        return isinstance(val, int)

    @classmethod
    def tg_2(cls, val: object) -> TypeIs[int]:
        return isinstance(val, int)

    @staticmethod
    def tg_3(val: object) -> TypeIs[int]:
        return isinstance(val, int)

    def tg4(self, val: object) -> TypeIs[Self]:
        return isinstance(val, type(self))

    def tg5(self: T_A, val: object) -> TypeIs[T_A]:
        return isinstance(val, type(self))

class B(A):
    pass

# > The type narrowing behavior is applied to the first positional argument
# > passed to the function. The function may accept additional arguments,
# > but they are not affected by type narrowing.


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


# > If a type narrowing function
# > is implemented as an instance method or class method, the first positional
# > argument maps to the second parameter (after self or cls).

class C:
    # Type checker should emit error here.
    def tg_1(self) -> TypeIs[int]:  # E
        return False

    @classmethod
    # Type checker should emit error here.
    def tg_2(cls) -> TypeIs[int]:  # E
        return False

# > ``TypeIs`` is also valid as the return type of a callable, for example
# > in callback protocols and in the ``Callable`` special form. In these
# > contexts, it is treated as a subtype of bool. For example, ``Callable[..., TypeIs[int]]``
# > is assignable to ``Callable[..., bool]``.


def takes_callable_bool(f: Callable[[object], bool]) -> None:
    pass


def takes_callable_str(f: Callable[[object], str]) -> None:
    pass


def simple_typeguard(val: object) -> TypeIs[int]:
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

# TypeIs and TypeGuard are not compatible with each other.

def takes_typeguard(f: Callable[[object], TypeGuard[int]]) -> None:
    pass

def takes_typeis(f: Callable[[object], TypeIs[int]]) -> None:
    pass

def is_int_typeis(val: object) -> TypeIs[int]:
    return isinstance(val, int)

def is_int_typeguard(val: object) -> TypeGuard[int]:
    return isinstance(val, int)

takes_typeguard(is_int_typeguard)  # OK
takes_typeguard(is_int_typeis)     # E
takes_typeis(is_int_typeguard)     # E
takes_typeis(is_int_typeis)        # OK


# > Unlike ``TypeGuard``, ``TypeIs`` is invariant in its argument type:
# > ``TypeIs[B]`` is not a subtype of ``TypeIs[A]``,
# > even if ``B`` is a subtype of ``A``.

def takes_int_typeis(f: Callable[[object], TypeIs[int]]) -> None:
    pass


def int_typeis(val: object) -> TypeIs[int]:
    return isinstance(val, int)


def bool_typeis(val: object) -> TypeIs[bool]:
    return isinstance(val, bool)


takes_int_typeis(int_typeis)  # OK
takes_int_typeis(bool_typeis)  # E

# > It is an error to narrow to a type that is not consistent with the input type

def bad_typeis(x: int) -> TypeIs[str]:  # E
    return isinstance(x, str)


def bad_typeis_variance(x: list[object]) -> TypeIs[list[int]]:  # E
    return all(isinstance(x, int) for x in x)
