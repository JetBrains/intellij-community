"""
Tests for evaluation of calls to overloaded functions.
"""

from enum import Enum
from typing import Any, assert_type, Literal, overload, TypeVar

# mypy: disable-error-code=overload-overlap


T = TypeVar("T")

# > Step 1: Examine the argument list to determine the number of
# > positional and keyword arguments. Use this information to eliminate any
# > overload candidates that are not plausible based on their
# > input signatures.

# (There is no way to observe via conformance tests whether an implementation
# performs this step separately from the argument-type-testing step 2 below, so
# the separation of step 1 from step 2 is purely a presentation choice for the
# algorithm, not a conformance requirement.)


@overload
def example1_1(x: int, y: str) -> int: ...


@overload
def example1_1(x: str) -> str: ...


def example1_1(x: int | str, y: str = "") -> int | str:
    return 1


# > - If no candidate overloads remain, generate an error and stop.

example1_1()  # E: no matching overload

# > - If only one candidate overload remains, it is the winning match. Evaluate
# >   it as if it were a non-overloaded function call and stop.

ret1 = example1_1(1, "")
assert_type(ret1, int)

example1_1(1, 1)  # E: Literal[1] not assignable to str

ret3 = example1_1("")
assert_type(ret3, str)

example1_1(1)  # E: Literal[1] not assignable to str


@overload
def example1_2(b: Literal[True] = ...) -> int: ...


@overload
def example1_2(b: bool) -> float: ...


def example1_2(b: bool = True) -> float: ...


def check_example1_2() -> None:
    assert_type(example1_2(), int)


# > Step 2: Evaluate each remaining overload as a regular (non-overloaded)
# > call to determine whether it is compatible with the supplied
# > argument list. Unlike step 1, this step considers the types of the parameters
# > and arguments. During this step, do not generate any user-visible errors.
# > Simply record which of the overloads result in evaluation errors.


@overload
def example2(x: int, y: str, z: int) -> str: ...


@overload
def example2(x: int, y: int, z: int) -> int: ...


def example2(x: int, y: int | str, z: int) -> int | str:
    return 1


# > - If only one overload evaluates without error, it is the winning match.
# >   Evaluate it as if it were a non-overloaded function call and stop.

ret5 = example2(1, 2, 3)
assert_type(ret5, int)

# > Step 3: If step 2 produces errors for all overloads, perform
# > "argument type expansion". Union types can be expanded
# > into their constituent subtypes. For example, the type ``int | str`` can
# > be expanded into ``int`` and ``str``.

# > - If all argument lists evaluate successfully, combine their
# >   respective return types by union to determine the final return type
# >   for the call, and stop.


def check_expand_union(v: int | str) -> None:
    ret1 = example2(1, v, 1)
    assert_type(ret1, int | str)


# > - If argument expansion has been applied to all arguments and one or
# >   more of the expanded argument lists cannot be evaluated successfully,
# >   generate an error and stop.


def check_expand_union_2(v: int | str) -> None:
    example2(v, v, 1)  # E: no overload matches (str, ..., ...)


# > 2. ``bool`` should be expanded into ``Literal[True]`` and ``Literal[False]``.


@overload
def expand_bool(x: Literal[False]) -> Literal[0]: ...


@overload
def expand_bool(x: Literal[True]) -> Literal[1]: ...


def expand_bool(x: bool) -> int:
    return int(x)


def check_expand_bool(v: bool) -> None:
    ret1 = expand_bool(v)
    assert_type(ret1, Literal[0, 1])


# > 3. ``Enum`` types (other than those that derive from ``enum.Flag``) should
# > be expanded into their literal members.


class Color(Enum):
    RED = 1
    BLUE = 1


@overload
def expand_enum(x: Literal[Color.RED]) -> Literal[0]: ...


@overload
def expand_enum(x: Literal[Color.BLUE]) -> Literal[1]: ...


def expand_enum(x: Color) -> int:
    return x.value


def check_expand_enum(v: Color) -> None:
    ret1 = expand_enum(v)
    assert_type(ret1, Literal[0, 1])


# > 4. ``type[A | B]`` should be expanded into ``type[A]`` and ``type[B]``.


@overload
def expand_type_union(x: type[int]) -> int: ...


@overload
def expand_type_union(x: type[str]) -> str: ...


def expand_type_union(x: type[int] | type[str]) -> int | str:
    return 1


def check_expand_type_union(v: type[int | str]) -> None:
    ret1 = expand_type_union(v)
    assert_type(ret1, int | str)


# > 5. Tuples of known length that contain expandable types should be expanded
# > into all possible combinations of their element types. For example, the type
# > ``tuple[int | str, bool]`` should be expanded into ``(int, Literal[True])``,
# > ``(int, Literal[False])``, ``(str, Literal[True])``, and
# > ``(str, Literal[False])``.


@overload
def expand_tuple(x: tuple[int, int]) -> int: ...


@overload
def expand_tuple(x: tuple[int, str]) -> str: ...


def expand_tuple(x: tuple[int, int | str]) -> int | str:
    return 1


def check_expand_tuple(v: int | str) -> None:
    ret1 = expand_tuple((1, v))
    assert_type(ret1, int | str)


# > Step 4: If the argument list is compatible with two or more overloads,
# > determine whether one or more of the overloads has a variadic parameter
# > (either ``*args`` or ``**kwargs``) that maps to a corresponding argument
# > that supplies an indeterminate number of positional or keyword arguments.
# > If so, eliminate overloads that do not have a variadic parameter.


@overload
def variadic(x: int, /) -> str: ...


@overload
def variadic(x: int, y: int, /, *args: int) -> int: ...


def variadic(*args: int) -> int | str:
    return 1


# > - If this results in only one remaining candidate overload, it is
# >   the winning match. Evaluate it as if it were a non-overloaded function
# >   call and stop.


def check_variadic(v: list[int]) -> None:
    ret1 = variadic(*v)
    assert_type(ret1, int)


# > Step 5: For all arguments, determine whether all possible
# > :term:`materializations <materialize>` of the argument's type are assignable to
# > the corresponding parameter type for each of the remaining overloads. If so,
# > eliminate all of the subsequent remaining overloads.


@overload
def example4(x: list[int], y: int) -> list[int]: ...


@overload
def example4(x: list[str], y: str) -> list[int]: ...


@overload
def example4(x: int, y: int) -> list[str]: ...


def example4(x: list[int] | list[str] | int, y: int | str) -> list[int] | list[str]:
    return []


def check_example4(v1: list[Any], v2: Any) -> None:
    ret1 = example4(v1, v2)
    assert_type(ret1, list[int])

    ret2 = example4(v2, 1)
    assert_type(ret2, Any)


@overload
def example5(obj: list[int]) -> list[int]: ...


@overload
def example5(obj: list[str]) -> list[str]: ...


def example5(obj: Any) -> list[Any]:
    return []


def check_example5(b: list[Any]) -> None:
    assert_type(example5(b), Any)


@overload
def example6(a: int, b: Any) -> float: ...


@overload
def example6(a: float, b: T) -> T: ...


def example6(a: float, b: T) -> T: ...


def check_example6(a: list[Any], b: Any, c: str) -> None:
    m: list[int] = []

    # All possible materializations of list[Any] are
    # assignable to Any, so this matches the first overload
    # and eliminates all subsequent overloads.
    v1 = example6(1, a)
    assert_type(v1, float)

    # All possible materializations of Any are
    # assignable to Any, so this matches the first overload
    # and eliminates all subsequent overloads.
    v2 = example6(1, b)
    assert_type(v2, float)

    # All possible materializations of list[int] are
    # assignable to Any, so this matches the first overload
    # and eliminates all subsequent overloads.
    v3 = example6(1, m)
    assert_type(v3, float)

    v4 = example6(1.0, c)
    assert_type(v4, str)

    v5 = example6(1.0, b)
    assert_type(v5, Any)

    v6 = example6(1.0, m)
    assert_type(v6, list[int])


@overload
def example7(x: list[Any], y: int) -> list[int]: ...


@overload
def example7(x: list[Any], y: str) -> list[str]: ...


def example7(x: list[Any], y: int | str) -> list[int] | list[str]:
    return []


def check_example7(v1: list[Any], v2: Any) -> None:
    ret1 = example7(v1, 1)
    assert_type(ret1, list[int])

    ret2 = example7(v1, "")
    assert_type(ret2, list[str])

    ret3 = example7(v1, v2)
    assert_type(ret3, Any)
