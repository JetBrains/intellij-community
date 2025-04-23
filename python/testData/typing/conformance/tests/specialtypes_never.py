"""
Tests the handling of typing.Never and typing.NoReturn.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/special-types.html#noreturn

import sys
from typing import Any, Generic, Never, NoReturn, TypeVar

T = TypeVar("T")
T_co = TypeVar("T_co", covariant=True)
U = TypeVar("U")


def stop() -> NoReturn:
    raise RuntimeError("no way")


def func1(x: int) -> NoReturn:  # E: implicitly returns None
    if x != 0:
        sys.exit(1)


# > The checkers will also recognize that the code after calls to such functions
# > is unreachable and will behave accordingly.


def func2(x: int) -> int:
    if x > 0:
        return x
    stop()
    return "whatever works"  # No type error


# The spec previously said that NoReturn is only valid in a function return type,
# but this was removed and it should now be accepted in all of these contexts:


def func3(
    a: NoReturn, b: list[NoReturn]
) -> None:
    c: NoReturn = a


def func4(
    a: list[NoReturn],
) -> None:
    c: list[NoReturn] = a


def func5() -> list[NoReturn]:
    return []


class ClassA:
    x: NoReturn
    y: list[NoReturn]

    def __init__(self, x: NoReturn, y: list[NoReturn]) -> None:
        self.x = x
        self.y = y


# Never is compatible with all types.


def func6(a: Never):
    v1: int = a  # OK
    v2: str = a  # OK
    v3: list[str] = a  # OK


# Never is a synonym for NoReturn.


def func7(x: int) -> Never:
    sys.exit(1)


# Other types are not compatible with Never except for Never (and Any).


def func8(a: Never, b: Any, c: list[Never]):
    v1: Never = a  # OK
    v2: Never = b  # OK
    v3: list[int] = c  # E
    v4: Never = stop()  # OK


class ClassB(Generic[T_co]):
    pass


def func9(x: U) -> ClassB[U]:
    # Never is a bottom type and therefore compatible with a covariant type variable.
    return ClassB[Never]()  # OK


class ClassC(Generic[T]):
    pass


def func10(x: U) -> ClassC[U]:
    # Never is not compatible in an invariant context.
    return ClassC[Never]()  # E
