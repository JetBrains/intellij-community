"""
Tests interactions between Literal types and other typing features.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/literal.html#interactions-with-other-types-and-features
from enum import Enum
from typing import IO, Any, Final, Generic, Literal, TypeVar, LiteralString, assert_type, overload


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
    raise NotImplementedError


assert_type(open("path", "r"), IO[str])
assert_type(open("path", "wb"), IO[bytes])
assert_type(open("path", "other"), IO[Any])


A = TypeVar("A", bound=int)
B = TypeVar("B", bound=int)
C = TypeVar("C", bound=int)


class Matrix(Generic[A, B]):
    def __add__(self, other: "Matrix[A, B]") -> "Matrix[A, B]":
        raise NotImplementedError

    def __matmul__(self, other: "Matrix[B, C]") -> "Matrix[A, C]":
        raise NotImplementedError

    def transpose(self) -> "Matrix[B, A]":
        raise NotImplementedError


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


# > Type checkers may optionally perform additional analysis for both
# > enum and non-enum Literal types beyond what is described in the section above.
#
# > For example, it may be useful to perform narrowing based on things
# > like containment or equality checks:

def expects_bad_status(status: Literal["MALFORMED", "ABORTED"]):
    ...


def expects_pending_status(status: Literal["PENDING"]):
    ...


def parse_status2(status: LiteralString) -> None:
    if status == "MALFORMED":
        expects_bad_status(status)  # E? narrowing the type here is sound, but optional per the spec
    elif status == "ABORTED":
        expects_bad_status(status)  # E? narrowing the type here is sound, but optional per the spec

    if status in ("MALFORMED", "ABORTED"):
        expects_bad_status(status)  # E? narrowing the type here is sound, but optional per the spec

    if status == "PENDING":
        expects_pending_status(status)  # E? narrowing the type here is sound, but optional per the spec


# Narrowing `str` to `Literal` strings is unsound given the possiblity of
# user-defined `str` subclasses that could have custom equality semantics,
# but is explicitly listed by the spec as optional analysis that type checkers
# may perform.
def parse_status3(status: str) -> None:
    if status == "MALFORMED":
        expects_bad_status(status)  # E? narrowing the type here is unsound, but allowed per the spec
    elif status == "ABORTED":
        expects_bad_status(status)  # E? narrowing the type here is unsound, but allowed per the spec

    if status in ("MALFORMED", "ABORTED"):
        expects_bad_status(status)  # E? narrowing the type here is unsound, but allowed per the spec

    if status == "PENDING":
        expects_pending_status(status)  # E? narrowing the type here is unsound, but allowed per the spec


final_val1: Final = 3
assert_type(final_val1, Literal[3])

final_val2: Final = True
assert_type(final_val2, Literal[True])
