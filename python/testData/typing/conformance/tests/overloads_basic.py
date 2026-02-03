"""
Tests the behavior of typing.overload.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/overload.html#overload

from typing import (
    Any,
    Callable,
    Iterable,
    Iterator,
    TypeVar,
    assert_type,
    overload,
)


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

