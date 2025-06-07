"""
Tests type compatibility rules for tuples.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/tuples.html#type-compatibility-rules

# > Because tuple contents are immutable, the element types of a tuple are covariant.


from typing import Any, Iterable, Never, Sequence, TypeAlias, TypeVar, assert_type


def func1(t1: tuple[float, complex], t2: tuple[int, int]):
    v1: tuple[float, complex] = t2  # OK
    v2: tuple[int, int] = t1  # E


# > A homogeneous tuple of arbitrary length is equivalent
# > to a union of tuples of different lengths.


def func2(t1: tuple[int], t2: tuple[int, *tuple[int, ...]], t3: tuple[int, ...]):
    v1: tuple[int, ...]
    v1 = t1  # OK
    v1 = t2  # OK

    v2: tuple[int, *tuple[int, ...]]
    v2 = t1  # OK
    v2 = t3  # E

    v3: tuple[int]
    v3 = t2  # E
    v3 = t3  # E


# > The type ``tuple[Any, ...]`` is bidirectionally compatible with any tuple::


def func3(t1: tuple[int], t2: tuple[int, ...], t3: tuple[Any, ...]):
    v1: tuple[int, ...] = t1  # OK
    v2: tuple[Any, ...] = t1  # OK

    v3: tuple[int] = t2  # E
    v4: tuple[Any, ...] = t2  # OK

    v5: tuple[float, float] = t3  # OK
    v6: tuple[int, *tuple[str, ...]] = t3  # OK


from missing_module import SomeType  # type: ignore


def func4(
    untyped_iter: Iterable[Any],
    some_type_tuple: tuple[SomeType, ...],
    any_tuple: tuple[Any, ...],
    int_tuple: tuple[int, ...],
):
    a: tuple[int, int] = tuple(untyped_iter)  # OK
    b: tuple[int, int] = some_type_tuple  # OK
    c: tuple[int, int] = any_tuple  # OK
    d: tuple[int, int] = int_tuple  # E


# > The length of a tuple at runtime is immutable, so it is safe for type
# > checkers to use length checks to narrow the type of a tuple

# NOTE: This type narrowing functionality is optional, not mandated.

Func5Input: TypeAlias = tuple[int] | tuple[str, str] | tuple[int, *tuple[str, ...], int]

def func5(val: Func5Input):
    if len(val) == 1:
        # Type can be narrowed to tuple[int].
        assert_type(val, tuple[int])  # E[func5_1]
        assert_type(val, Func5Input)  # E[func5_1]

    if len(val) == 2:
        # Type can be narrowed to tuple[str, str] | tuple[int, int].
        assert_type(val, tuple[str, str] | tuple[int, int]) # E[func5_2]
        assert_type(val, Func5Input)  # E[func5_2]

    if len(val) == 3:
        # Type can be narrowed to tuple[int, str, int].
        assert_type(val, tuple[int, str, int]) # E[func5_3]
        assert_type(val, Func5Input)  # E[func5_3]


# > This property may also be used to safely narrow tuple types within a match
# > statement that uses sequence patterns.

# NOTE: This type narrowing functionality is optional, not mandated.

Func6Input: TypeAlias = tuple[int] | tuple[str, str] | tuple[int, *tuple[str, ...], int]


def func6(val: Func6Input):
    match val:
        case (x,):
            # Type may be narrowed to tuple[int].
            assert_type(val, tuple[int])  # E[func6_1]
            assert_type(val, Func6Input)  # E[func6_1]

        case (x, y):
            # Type may be narrowed to tuple[str, str] | tuple[int, int].
            assert_type(val, tuple[str, str] | tuple[int, int]) # E[func6_2]
            assert_type(val, Func6Input)  # E[func6_2]

        case (x, y, z):
            # Type may be narrowed to tuple[int, str, int].
            assert_type(val, tuple[int, str, int]) # E[func6_3]
            assert_type(val, Func6Input)  # E[func6_3]


# > Type checkers may safely use this equivalency rule (tuple expansion)
# > when narrowing tuple types

# NOTE: This type narrowing functionality is optional, not mandated.

Func7Input: TypeAlias = tuple[int | str, int | str]


def func7(subj: Func7Input):
    match subj:
        case x, str():
            assert_type(subj, tuple[int | str, str]) # E[func7_1]
            assert_type(subj, Func7Input)  # E[func7_1]
        case y:
            assert_type(subj, tuple[int | str, int]) # E[func7_2]
            assert_type(subj, Func7Input)  # E[func7_2]


# > The tuple class derives from Sequence[T_co] where ``T_co`` is a covariant
# (non-variadic) type variable. The specialized type of ``T_co`` should be
# computed by a type checker as a a supertype of all element types.

# > A zero-length tuple (``tuple[()]``) is a subtype of ``Sequence[Never]``.

T = TypeVar("T")


def test_seq(x: Sequence[T]) -> Sequence[T]:
    return x


def func8(
    t1: tuple[complex, list[int]], t2: tuple[int, *tuple[str, ...]], t3: tuple[()]
):
    assert_type(
        test_seq(t1), Sequence[complex | list[int]]
    )  # Could be Sequence[object]
    assert_type(test_seq(t2), Sequence[int | str])  # Could be Sequence[object]
    assert_type(test_seq(t3), Sequence[Never])


t1: tuple[int, *tuple[str]] = (1, "")  # OK
t1 = (1, "", "")  # E

t2: tuple[int, *tuple[str, ...]] = (1,)  # OK
t2 = (1, "")  # OK
t2 = (1, "", "")  # OK
t2 = (1, 1, "")  # E
t2 = (1, "", 1)  # E


t3: tuple[int, *tuple[str, ...], int] = (1, 2)  # OK
t3 = (1, "", 2)  # OK
t3 = (1, "", "", 2)  # OK
t3 = (1, "", "")  # E
t3 = (1, "", "", 1.2)  # E

t4: tuple[*tuple[str, ...], int] = (1,)  # OK
t4 = ("", 1)  # OK
t4 = ("", "", 1)  # OK
t4 = (1, "", 1)  # E
t4 = ("", "", 1.2)  # E


def func9(a: tuple[str, str]):
    t1: tuple[str, str, *tuple[int, ...]] = a  # OK
    t2: tuple[str, str, *tuple[int]] = a  # E
    t3: tuple[str, *tuple[str, ...]] = a  # OK
    t4: tuple[str, str, *tuple[str, ...]] = a  # OK
    t5: tuple[str, str, str, *tuple[str, ...]] = a  # E
    t6: tuple[str, *tuple[int, ...], str] = a  # OK
    t7: tuple[*tuple[str, ...], str] = a  # OK
    t8: tuple[*tuple[str, ...], str] = a  # OK
    t9: tuple[*tuple[str, ...], str, str, str] = a  # E
