"""
Tests semantics of ParamSpec.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#semantics


from typing import Callable, Concatenate, Generic, ParamSpec, TypeVar, assert_type

P = ParamSpec("P")


def changes_return_type_to_str(x: Callable[P, int]) -> Callable[P, str]:
    ...


def returns_int(a: str, b: bool, /) -> int:
    ...


f1 = changes_return_type_to_str(returns_int)
assert_type(f1, Callable[[str, bool], str])

v1 = f1("A", True)  # OK
assert_type(v1, str)
f1(a="A", b=True)  # E: positional-only
f1("A", "A")  # E: wrong type


def func1(x: Callable[P, int], y: Callable[P, int]) -> Callable[P, bool]:
    ...


def x_y(x: int, y: str) -> int:
    ...


def y_x(y: int, x: str) -> int:
    ...


f2 = func1(x_y, x_y)
assert_type(f2(1, ""), bool)
assert_type(f2(y="", x=1), bool)

f3 = func1(x_y, y_x)  # E?: Could return (a: int, b: str, /) -> bool
# (a callable with two positional-only parameters)
# This works because both callables have types that are
# behavioral subtypes of Callable[[int, str], int]
# Also OK for type checkers to reject this.


def keyword_only_x(*, x: int) -> int:
    ...


def keyword_only_y(*, y: int) -> int:
    ...


func1(keyword_only_x, keyword_only_y)  # E


U = TypeVar("U")


class Y(Generic[U, P]):
    f: Callable[P, str]
    prop: U

    def __init__(self, f: Callable[P, str], prop: U) -> None:
        self.f = f
        self.prop = prop


def callback_a(q: int, /) -> str:
    ...


def func(x: int) -> None:
    y1 = Y(callback_a, x)
    assert_type(y1, Y[int, [int]])
    y2 = y1.f
    assert_type(y2, Callable[[int], str])


def bar(x: int, *args: bool) -> int:
    ...


def add(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]:
    ...


a1 = add(bar)  # Should return (a: str, /, x: int, *args: bool) -> bool
assert_type(a1("", 1, False, True), bool)
assert_type(a1("", x=1), bool)
a1(1, x=1)  # E


def remove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]:
    ...


r1 = remove(bar)  # Should return (*args: bool) -> bool
assert_type(r1(False, True, True), bool)
assert_type(r1(), bool)
r1(1)  # E


def transform(
    x: Callable[Concatenate[int, P], int]
) -> Callable[Concatenate[str, P], bool]:
    ...


t1 = transform(bar)  # Should return (a: str, /, *args: bool) -> bool
assert_type(t1("", True, False, True), bool)
assert_type(t1(""), bool)
t1(1)  # E


def expects_int_first(x: Callable[Concatenate[int, P], int]) -> None:
    ...


@expects_int_first  # E
def one(x: str) -> int:
    ...


@expects_int_first  # E
def two(*, x: int) -> int:
    ...


@expects_int_first  # E
def three(**kwargs: int) -> int:
    ...


@expects_int_first  # OK
def four(*args: int) -> int:
    ...
