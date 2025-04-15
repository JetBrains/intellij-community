from collections.abc import Iterable
from typing import Any, Callable, Concatenate, ParamSpec, TypeVar, Union, assert_type

S = TypeVar("S")
T = TypeVar("T")
P = ParamSpec("P")
R = TypeVar("R")

TFloat = TypeVar("TFloat", bound=float)

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

var1 = 3
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
