"""
Tests the use of TypeVarTuple within a Callable.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#type-variable-tuples-with-callable

# > Type variable tuples can also be used in the arguments section of a Callable.


from typing import Callable, TypeVar, TypeVarTuple, assert_type

Ts = TypeVarTuple("Ts")
T = TypeVar("T")


class Process:
    def __init__(self, target: Callable[[*Ts], None], args: tuple[*Ts]) -> None:
        ...


def func1(arg1: int, arg2: str) -> None:
    ...


Process(target=func1, args=(0, ""))  # OK
Process(target=func1, args=("", 0))  # E


def func2(f: Callable[[int, *Ts, T], tuple[T, *Ts]]) -> tuple[*Ts, T]:
    ...


def callback1(a: int, b: str, c: int, d: complex) -> tuple[complex, str, int]:
    ...


def callback2(a: int, d: str) -> tuple[str]:
    ...


assert_type(func2(callback1), tuple[str, int, complex])
assert_type(func2(callback2), tuple[str])


def func3(*args: * tuple[int, *Ts, T]) -> tuple[T, *Ts]:
    ...


assert_type(func3(1, "", 3j, 3.4), tuple[float, str, complex])
