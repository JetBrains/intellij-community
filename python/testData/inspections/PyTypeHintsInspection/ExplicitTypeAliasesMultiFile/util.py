from typing import Any, Callable, Concatenate, Literal, ParamSpec, TypeVar, Union, \
    assert_type, Never
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

var1 = 3
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