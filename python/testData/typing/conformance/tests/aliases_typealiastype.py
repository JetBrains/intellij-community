"""
Tests the TypeAliasType call introduced in Python 3.12.
"""

from typing import Callable, Generic, ParamSpec, TypeAliasType, TypeVar, TypeVarTuple

S = TypeVar("S")
T = TypeVar("T")
TStr = TypeVar("TStr", bound=str)
P = ParamSpec("P")
Ts = TypeVarTuple("Ts")

my_tuple = (S, T)
var1 = 3

GoodAlias1 = TypeAliasType("GoodAlias1", int)
GoodAlias2 = TypeAliasType("GoodAlias2", list[T], type_params=(T,))
GoodAlias3 = TypeAliasType("GoodAlias3", list[T] | list[S], type_params=(S, T))
GoodAlias4 = TypeAliasType("GoodAlias4", T | list[GoodAlias4[T]], type_params=(T,))
GoodAlias5 = TypeAliasType(
    "GoodAlias5",
    Callable[P, TStr] | list[S] | list[GoodAlias5[S, TStr, P]] | tuple[*Ts],
    type_params=(S, TStr, P, Ts),
)

class ClassA(Generic[T]):
    GoodAlias6 = TypeAliasType("GoodAlias6", list[T])


print(GoodAlias1.__value__)  # OK
print(GoodAlias1.__type_params__)  # OK
print(GoodAlias1.other_attrib)  # E: unknown attribute


x1: GoodAlias4[int] = 1  # OK
x2: GoodAlias4[int] = [1]  # OK
x3: GoodAlias5[str, str, ..., int, str]  # OK
x4: GoodAlias5[int, str, ..., int, str]  # OK
x5: GoodAlias5[int, str, [int, str], *tuple[int, str, int]]  # OK
x6: GoodAlias5[int, int, ...]  # E: incorrect type arguments


BadAlias1 = TypeAliasType("BadAlias1", list[S], type_params=(T,))  # E: S not in scope
BadAlias2 = TypeAliasType("BadAlias2", list[S])  # E: S not in scope
BadAlias3 = TypeAliasType("BadAlias3", int, type_params=my_tuple)  # E: not literal tuple
BadAlias4 = TypeAliasType("BadAlias4", BadAlias4)  # E: circular dependency
BadAlias5 = TypeAliasType("BadAlias5", T | BadAlias5[str], type_params=(T,))  # E: circular dependency
BadAlias6 = TypeAliasType("BadAlias6", BadAlias7)  # E: circular dependency
BadAlias7 = TypeAliasType("BadAlias7", BadAlias6)  # E?: circular dependency

# The following are invalid type expressions for a type alias.
BadAlias8 = TypeAliasType("BadAlias8", eval("".join(map(chr, [105, 110, 116]))))  # E
BadAlias9 = TypeAliasType("BadAlias9", [int, str])  # E
BadAlias10 = TypeAliasType("BadAlias10", ((int, str),))  # E
BadAlias11 = TypeAliasType("BadAlias11", [int for i in range(1)])  # E
BadAlias12 = TypeAliasType("BadAlias12", {"a": "b"})  # E
BadAlias13 = TypeAliasType("BadAlias13", (lambda: int)())  # E
BadAlias14 = TypeAliasType("BadAlias14", [int][0])  # E
BadAlias15 = TypeAliasType("BadAlias15", int if 1 < 3 else str)  # E
BadAlias16 = TypeAliasType("BadAlias16", var1)  # E
BadAlias17 = TypeAliasType("BadAlias17", True)  # E
BadAlias18 = TypeAliasType("BadAlias18", 1)  # E
BadAlias19 = TypeAliasType("BadAlias19", list or set)  # E
BadAlias20 = TypeAliasType("BadAlias20", f"{'int'}")  # E
