"""
Tests the basic typing.overload behavior described in PEP 484.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/overload.html#overload

# Note: The behavior of @overload is severely under-specified by PEP 484 leading
# to significant divergence in behavior across type checkers. This is something
# we will likely want to address in a future update to the typing spec. For now,
# this conformance test will cover only the most basic functionality described
# in PEP 484.

from typing import Any, Callable, Iterable, Iterator, TypeVar, assert_type, overload


class Bytes:
    ...

    @overload
    def __getitem__(self, __i: int) -> int:
        ...

    @overload
    def __getitem__(self, __s: slice) -> bytes:
        ...

    def __getitem__(self, __i_or_s: int | slice) -> int | bytes:
        if isinstance(__i_or_s, int):
            return 0
        else:
            return b""


b = Bytes()
assert_type(b[0], int)
assert_type(b[0:1], bytes)
b[""]  # E: no matching overload


T1 = TypeVar("T1")
T2 = TypeVar("T2")
S = TypeVar("S")


@overload
def map(func: Callable[[T1], S], iter1: Iterable[T1]) -> Iterator[S]:
    ...


@overload
def map(
    func: Callable[[T1, T2], S], iter1: Iterable[T1], iter2: Iterable[T2]
) -> Iterator[S]:
    ...


def map(func: Any, iter1: Any, iter2: Any = ...) -> Any:
    pass


# At least two overload signatures should be provided.
@overload  # E[func1]
def func1() -> None:  # E[func1]: At least two overloads must be present
    ...


def func1() -> None:
    pass


# > In regular modules, a series of @overload-decorated definitions must be
# > followed by exactly one non-@overload-decorated definition (for the same
# > function/method).
@overload  # E[func2]
def func2(x: int) -> int:  # E[func2]: no implementation
    ...


@overload
def func2(x: str) -> str:
    ...
