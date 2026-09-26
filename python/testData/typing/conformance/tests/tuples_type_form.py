"""
Tests the handling of builtins.tuple.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/tuples.html#tuple-type-form

# > The type of a tuple can be expressed by listing the element types.
from typing import Literal


t1: tuple[int] = (1,)  # OK
t1 = (1, 2)  # E
t2: tuple[int, int] = (1, 2)  # OK
t2 = (1,)  # E
t2 = (1, "")  # E


def func1() -> tuple[Literal[1], Literal[2]]:
    return (1, 2)


# > The empty tuple can be typed as tuple[()].

t10: tuple[()] = ()  # OK
t10 = (1,)  # E


def func2() -> list[tuple[()]]:
    return [(), (), ()]


# > Arbitrary-length homogeneous tuples can be expressed using one type and ellipsis.
t20: tuple[int, ...] = ()  # OK
t20 = (1,)  # OK
t20 = (1, 2, 3, 4)  # OK
t20 = (1, 2, 3, "")  # E


t30: tuple[int, ...]  # OK
t31: tuple[int, int, ...]  # E
t32: tuple[...]  # E
t33: tuple[..., int]  # E
t34: tuple[int, ..., int]  # E
t35: tuple[*tuple[str], ...]  # E
t36: tuple[*tuple[str, ...], ...]  # E
