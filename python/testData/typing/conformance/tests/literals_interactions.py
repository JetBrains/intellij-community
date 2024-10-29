"""
Tests interactions between Literal types and other typing features.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/literal.html#interactions-with-other-types-and-features

from enum import Enum
from typing import IO, Any, Final, Generic, Literal, TypeVar, assert_type, overload


def func1(v: tuple[int, str, list[bool]], a: Literal[0], b: Literal[5], c: Literal[-5]):
    assert_type(v[a], int)
    assert_type(v[2], list[bool])

    v[b]  # E: index out of range
    v[c]  # E: index out of range
    v[4]  # E: index out of range
    v[-4]  # E: index out of range


_PathType = str | bytes | int


@overload
def open(
    path: _PathType,
    mode: Literal["r", "w", "a", "x", "r+", "w+", "a+", "x+"],
) -> IO[str]:
    ...


@overload
def open(
    path: _PathType,
    mode: Literal["rb", "wb", "ab", "xb", "r+b", "w+b", "a+b", "x+b"],
) -> IO[bytes]:
    ...


@overload
def open(path: _PathType, mode: str) -> IO[Any]:
    ...


def open(path: _PathType, mode: Any) -> Any:
    pass


assert_type(open("path", "r"), IO[str])
assert_type(open("path", "wb"), IO[bytes])
assert_type(open("path", "other"), IO[Any])


A = TypeVar("A", bound=int)
B = TypeVar("B", bound=int)
C = TypeVar("C", bound=int)


class Matrix(Generic[A, B]):
    def __add__(self, other: "Matrix[A, B]") -> "Matrix[A, B]":
        ...

    def __matmul__(self, other: "Matrix[B, C]") -> "Matrix[A, C]":
        ...

    def transpose(self) -> "Matrix[B, A]":
        ...


def func2(a: Matrix[Literal[2], Literal[3]], b: Matrix[Literal[3], Literal[7]]):
    c = a @ b
    assert_type(c, Matrix[Literal[2], Literal[7]])


T = TypeVar("T", Literal["a"], Literal["b"], Literal["c"])
S = TypeVar("S", bound=Literal["foo"])


class Status(Enum):
    SUCCESS = 0
    INVALID_DATA = 1
    FATAL_ERROR = 2


def parse_status1(s: str | Status) -> None:
    if s is Status.SUCCESS:
        assert_type(s, Literal[Status.SUCCESS])
    elif s is Status.INVALID_DATA:
        assert_type(s, Literal[Status.INVALID_DATA])
    elif s is Status.FATAL_ERROR:
        assert_type(s, Literal[Status.FATAL_ERROR])
    else:
        assert_type(s, str)


def expects_bad_status(status: Literal["MALFORMED", "ABORTED"]):
    ...


def expects_pending_status(status: Literal["PENDING"]):
    ...


def parse_status2(status: str) -> None:
    if status in ("MALFORMED", "ABORTED"):
        return expects_bad_status(status)

    if status == "PENDING":
        expects_pending_status(status)


final_val1: Final = 3
assert_type(final_val1, Literal[3])

final_val2: Final = True
assert_type(final_val2, Literal[True])
