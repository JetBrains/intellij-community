"""
Tests the "type" statement introduced in Python 3.12.
"""

from typing import Callable, TypeVar


type GoodAlias1 = int
type GoodAlias2[S1, *S2, **S3] = Callable[S3, S1] | tuple[*S2]
type GoodAlias3 = GoodAlias2[int, tuple[int, str], ...]


class ClassA:
    type GoodAlias4 = int | None


GoodAlias1.bit_count  # E: cannot access attribute

GoodAlias1()  # E: cannot call alias

print(GoodAlias1.__value__)  # OK
print(GoodAlias1.__type_params__)  # OK
print(GoodAlias1.other_attrib)  # E: unknown attribute


class DerivedInt(GoodAlias1):  # E: cannot use alias as base class
    pass


def func2(x: object):
    if isinstance(x, GoodAlias1):  # E: cannot use alias in isinstance
        pass

var1 = 1

# The following should not be allowed as type aliases.
type BadTypeAlias1 = eval("".join(map(chr, [105, 110, 116])))  # E
type BadTypeAlias2 = [int, str]  # E
type BadTypeAlias3 = ((int, str),)  # E
type BadTypeAlias4 = [int for i in range(1)]  # E
type BadTypeAlias5 = {"a": "b"}  # E
type BadTypeAlias6 = (lambda: int)()  # E
type BadTypeAlias7 = [int][0]  # E
type BadTypeAlias8 = int if 1 < 3 else str  # E
type BadTypeAlias9 = var1  # E
type BadTypeAlias10 = True  # E
type BadTypeAlias11 = 1  # E
type BadTypeAlias12 = list or set  # E
type BadTypeAlias13 = f"{'int'}"  # E

type BadTypeAlias14 = int  # E[TA14]: redeclared
type BadTypeAlias14 = int  # E[TA14]: redeclared


def func3():
    type BadTypeAlias15 = int  # E: alias not allowed in function



V = TypeVar("V")

type TA1[K] = dict[K, V] # E: combines old and new TypeVars


T1 = TypeVar("T1")

type TA2 = list[T1] # E: uses old TypeVar


type RecursiveTypeAlias1[T] = T | list[RecursiveTypeAlias1[T]]

r1_1: RecursiveTypeAlias1[int] = 1
r1_2: RecursiveTypeAlias1[int] = [1, [1, 2, 3]]

type RecursiveTypeAlias2[S: int, T: str, **P] = Callable[P, T] | list[S] | list[RecursiveTypeAlias2[S, T, P]]

r2_1: RecursiveTypeAlias2[str, str, ...] # E: not compatible with S bound
r2_2: RecursiveTypeAlias2[int, str, ...]
r2_3: RecursiveTypeAlias2[int, int, ...] # E: not compatible with T bound
r2_4: RecursiveTypeAlias2[int, str, [int, str]]

type RecursiveTypeAlias3 = RecursiveTypeAlias3 # E: circular definition

type RecursiveTypeAlias4[T] = T | RecursiveTypeAlias4[str] # E: circular definition

type RecursiveTypeAlias5[T] = T | list[RecursiveTypeAlias5[T]]

type RecursiveTypeAlias6 = RecursiveTypeAlias7  # E[RTA6+]: circular definition
type RecursiveTypeAlias7 = RecursiveTypeAlias6  # E[RTA6+]: circular definition
