"""
Tests traditional implicit type aliases.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/aliases.html

from collections.abc import Iterable
from typing import Any, Callable, Concatenate, ParamSpec, TypeVar, Union, assert_type

TFloat = TypeVar("TFloat", bound=float)
Vector = Iterable[tuple[TFloat, TFloat]]


def in_product(v: Vector[TFloat]) -> Iterable[TFloat]:
    return [x for x, _ in v]


def dilate(v: Vector[float], scale: float) -> Vector[float]:
    return ((x * scale, y * scale) for x, y in v)


# > Type aliases may be as complex as type hints in annotations – anything
# > that is acceptable as a type hint is acceptable in a type alias.

S = TypeVar("S")
T = TypeVar("T")
P = ParamSpec("P")
R = TypeVar("R")

GoodTypeAlias1 = Union[int, str]
GoodTypeAlias2 = int | None
GoodTypeAlias3 = list[GoodTypeAlias2]
GoodTypeAlias4 = list[T]
GoodTypeAlias5 = tuple[T, ...] | list[T]
GoodTypeAlias6 = tuple[int, int, S, T]
GoodTypeAlias7 = Callable[..., int]
GoodTypeAlias8 = Callable[[int, T], T]
GoodTypeAlias9 = Callable[Concatenate[int, P], R]
GoodTypeAlias10 = Any
GoodTypeAlias11 = GoodTypeAlias1 | GoodTypeAlias2 | list[GoodTypeAlias4[int]]
GoodTypeAlias12 = list[TFloat]
GoodTypeAlias13 = Callable[P, None]


def good_type_aliases(
    p1: GoodTypeAlias1,
    p2: GoodTypeAlias2,
    p3: GoodTypeAlias3,
    p4: GoodTypeAlias4[int],
    p5: GoodTypeAlias5[str],
    p6: GoodTypeAlias6[int, str],
    p7: GoodTypeAlias7,
    p8: GoodTypeAlias8[str],
    p9: GoodTypeAlias9[[str, str], None],
    p10: GoodTypeAlias10,
    p11: GoodTypeAlias11,
    p12: GoodTypeAlias12[bool],
    p13: GoodTypeAlias13
):
    assert_type(p1, int | str)
    assert_type(p2, int | None)
    assert_type(p3, list[int | None])
    assert_type(p4, list[int])
    assert_type(p5, tuple[str, ...] | list[str])
    assert_type(p6, tuple[int, int, int, str])
    assert_type(p7, Callable[..., int])
    assert_type(p8, Callable[[int, str], str])
    assert_type(p9, Callable[[int, str, str], None])
    assert_type(p10, Any)
    assert_type(p11, int | str | None | list[list[int]])
    assert_type(p12, list[bool])
    assert_type(p13, Callable[..., None])


def good_type_aliases_used_badly(
    p1: GoodTypeAlias2[int],  # E: type alias is not generic
    p2: GoodTypeAlias3[int],  # E: type alias is already specialized
    p3: GoodTypeAlias4[int, int],  # E: too many type arguments
    p4: GoodTypeAlias8[int, int],  # E: too many type arguments
    p5: GoodTypeAlias9[int, int],  # E: bad type argument for ParamSpec
    p6: GoodTypeAlias12[str],  # E: type argument doesn't match bound
):
    pass


var1 = 3

# The following should not be considered type aliases.
BadTypeAlias1 = eval("".join(map(chr, [105, 110, 116])))
BadTypeAlias2 = [int, str]
BadTypeAlias3 = ((int, str),)
BadTypeAlias4 = [int for i in range(1)]
BadTypeAlias5 = {"a": "b"}
BadTypeAlias6 = (lambda: int)()
BadTypeAlias7 = [int][0]
BadTypeAlias8 = int if 1 < 3 else str
BadTypeAlias9 = var1
BadTypeAlias10 = True
BadTypeAlias11 = 1
BadTypeAlias12 = list or set
BadTypeAlias13 = f"int"
BadTypeAlias14 = "int | str"


def bad_type_aliases(
    p1: BadTypeAlias1,  # E: Invalid type annotation
    p2: BadTypeAlias2,  # E: Invalid type annotation
    p3: BadTypeAlias3,  # E: Invalid type annotation
    p4: BadTypeAlias4,  # E: Invalid type annotation
    p5: BadTypeAlias5,  # E: Invalid type annotation
    p6: BadTypeAlias6,  # E: Invalid type annotation
    p7: BadTypeAlias7,  # E: Invalid type annotation
    p8: BadTypeAlias8,  # E: Invalid type annotation
    p9: BadTypeAlias9,  # E: Invalid type annotation
    p10: BadTypeAlias10,  # E: Invalid type annotation
    p11: BadTypeAlias11,  # E: Invalid type annotation
    p12: BadTypeAlias12,  # E: Invalid type annotation
    p13: BadTypeAlias13,  # E: Invalid type annotation
    p14: BadTypeAlias14,  # E: Invalid type annotation
):
    pass


ListAlias = list
ListOrSetAlias = list | set

x1: list[str] = ListAlias()  # OK
assert_type(x1, list[str])

x2 = ListAlias[int]()  # OK
assert_type(x2, list[int])

x3 = ListOrSetAlias()  # E: cannot instantiate union

x4: ListOrSetAlias[int]  # E: already specialized
