"""
Tests explicit type aliases defined with `TypeAlias`.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/aliases.html#typealias

from typing import Any, Callable, Concatenate, Literal, ParamSpec, TypeVar, Union, assert_type
from typing import TypeAlias as TA

S = TypeVar("S")
T = TypeVar("T")
P = ParamSpec("P")
R = TypeVar("R")

GoodTypeAlias1: TA = Union[int, str]
GoodTypeAlias2: TA = int | None
GoodTypeAlias3: TA = list[GoodTypeAlias2]
GoodTypeAlias4: TA = list[T]
GoodTypeAlias5: TA = tuple[T, ...] | list[T]
GoodTypeAlias6: TA = tuple[int, int, S, T]
GoodTypeAlias7: TA = Callable[..., int]
GoodTypeAlias8: TA = Callable[[int, T], T]
GoodTypeAlias9: TA = Callable[Concatenate[int, P], R]
GoodTypeAlias10: TA = Any
GoodTypeAlias11: TA = GoodTypeAlias1 | GoodTypeAlias2 | list[GoodTypeAlias4[int]]
GoodTypeAlias12: TA = Callable[P, None]
GoodTypeAlias13: TA = "int | str"
GoodTypeAlias14: TA = list["int | str"]
GoodTypeAlias15: TA = Literal[3, 4, 5, None]


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
    p12: GoodTypeAlias12,
    p13: GoodTypeAlias13,
    p14: GoodTypeAlias14,
    p15: GoodTypeAlias15,
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
    assert_type(p12, Callable[..., None])
    assert_type(p13, int | str)
    assert_type(p14, list[int | str])
    assert_type(p15, Literal[3, 4, 5, None])


def good_type_aliases_used_badly(
    p1: GoodTypeAlias2[int],  # E: type alias is not generic
    p2: GoodTypeAlias3[int],  # E: type alias is already specialized
    p3: GoodTypeAlias4[int, int],  # E: too many type arguments
    p4: GoodTypeAlias8[int, int],  # E: too many type arguments
    p5: GoodTypeAlias9[int, int],  # E: bad type argument for ParamSpec
):
    pass


var1 = 3

# The following should not be allowed as type aliases.
BadTypeAlias1: TA = eval("".join(map(chr, [105, 110, 116])))  # E
BadTypeAlias2: TA = [int, str]  # E
BadTypeAlias3: TA = ((int, str),)  # E
BadTypeAlias4: TA = [int for i in range(1)]  # E
BadTypeAlias5: TA = {"a": "b"}  # E
BadTypeAlias6: TA = (lambda: int)()  # E
BadTypeAlias7: TA = [int][0]  # E
BadTypeAlias8: TA = int if 1 < 3 else str  # E
BadTypeAlias9: TA = var1  # E
BadTypeAlias10: TA = True  # E
BadTypeAlias11: TA = 1  # E
BadTypeAlias12: TA = list or set  # E
BadTypeAlias13: TA = f"{'int'}"  # E


ListAlias: TA = list
ListOrSetAlias: TA = list | set

x1: list[str] = ListAlias()  # OK
assert_type(x1, list[str])

x2: ListAlias[int]  # E: already specialized
x3 = ListOrSetAlias()  # E: cannot instantiate union
x4: ListOrSetAlias[int]  # E: already specialized
