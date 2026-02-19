"""
Tests the typing.Any special type.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/special-types.html#any

# > Every type is consistent with Any.

from typing import Any, Callable, assert_type


val1: Any
val1 = ""  # OK
val1 = 1  # OK

val2: list[Any]
val2 = [""]  # OK
val2 = [1]  # OK


def func1(val1: Any, val2: list[Any]) -> int:
    t1: int = val1  # OK
    t2: str = val1  # OK

    t3: list[str] = val2  # OK
    t4: list[int] = val2  # OK

    return val1  # OK


# > A function parameter without an annotation is assumed to be annotated with Any.


def func2(val):
    assert_type(val, Any)

    t1: int = val  # OK
    t2: str = val  # OK

    return 1  # OK


# > If a generic type is used without specifying type parameters, they are
# > assumed to be Any.


def func3(val1: list, val2: dict) -> None:
    assert_type(val1, list[Any])
    assert_type(val2, dict[Any, Any])

    t1: list[str] = val1  # OK
    t2: list[int] = val1  # OK

    t3: dict[str, str] = val2  # OK


# > This rule also applies to tuple, in annotation context it is equivalent
# > to tuple[Any, ...].


def func4(val1: tuple) -> None:
    assert_type(val1, tuple[Any, ...])

    t1: tuple[str, ...] = val1  # OK
    t2: tuple[int, ...] = val1  # OK


# > As well, a bare Callable in an annotation is equivalent to Callable[..., Any].


def func5(val1: Callable):
    assert_type(val1, Callable[..., Any])

    t1: Callable[[], Any] = val1  # OK
    t2: Callable[[int, str], None] = val1  # OK


# > Any can also be used as a base class. This can be useful for avoiding type
# > checker errors with classes that can duck type anywhere or are highly dynamic.

class ClassA(Any):
    def method1(self) -> int:
        return 1

a = ClassA()
assert_type(a.method1(), int)
assert_type(a.method2(), Any)
assert_type(ClassA.method3(), Any)
