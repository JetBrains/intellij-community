"""
Tests recursive (self-referential) type aliases.
"""

# The typing specification doesn't mandate support for recursive
# (self-referential) type aliases prior to PEP 695, but it also
# doesn't indicates that they shouldn't work.
# Most type checkers now support them, and many libraries and code
# bases have started to rely on them.
# PEP 695 formally mandates that recursive type aliases work.

from typing import Mapping, TypeAlias, TypeVar, Union

Json = Union[None, int, str, float, list["Json"], dict[str, "Json"]]

j1: Json = [1, {"a": 1}]  # OK
j2: Json = 3.4  # OK
j3: Json = [1.2, None, [1.2, [""]]]  # OK
j4: Json = {"a": 1, "b": 3j}  # E: incompatible type
j5: Json = [2, 3j]  # E: incompatible type


# This type alias should be equivalent to Json.
Json2 = Union[None, int, str, float, list["Json2"], dict[str, "Json2"]]

def func1(j1: Json) -> Json2:
    return j1


RecursiveTuple = str | int | tuple["RecursiveTuple", ...]


t1: RecursiveTuple = (1, 1)  # OK
t2: RecursiveTuple = (1, "1")  # OK
t3: RecursiveTuple = (1, "1", 1, "2")  # OK
t4: RecursiveTuple = (1, ("1", 1), "2")  # OK
t5: RecursiveTuple = (1, ("1", 1), (1, (1, 2)))  # OK
t6: RecursiveTuple = (1, ("1", 1), (1, (1, [2])))  # E
t6: RecursiveTuple = (1, [1])  # E


RecursiveMapping = str | int | Mapping[str, "RecursiveMapping"]

m1: RecursiveMapping = 1  # OK
m2: RecursiveMapping = "1"  # OK
m3: RecursiveMapping = {"1": "1"}  # OK
m4: RecursiveMapping = {"1": "1", "2": 1}  # OK
m5: RecursiveMapping = {"1": "1", "2": 1, "3": {}}  # OK
m6: RecursiveMapping = {"1": "1", "2": 1, "3": {"0": "0", "1": "2", "2": {}}}  # OK
m7: RecursiveMapping = {"1": [1]}  # E
m8: RecursiveMapping = {"1": "1", "2": 1, "3": [1, 2]}  # E
m9: RecursiveMapping = {"1": "1", "2": 1, "3": {"0": "0", "1": 1, "2": [1, 2, 3]}}  # E


T1 = TypeVar("T1", str, int)
T2 = TypeVar("T2")

GenericTypeAlias1 = list["GenericTypeAlias1[T1]" | T1]
SpecializedTypeAlias1 = GenericTypeAlias1[str]

g1: SpecializedTypeAlias1 = ["hi", ["hi", "hi"]]  # OK
g2: GenericTypeAlias1[str] = ["hi", "bye", [""], [["hi"]]]  # OK
g3: GenericTypeAlias1[str] = ["hi", [2.4]]  # E

GenericTypeAlias2 = list["GenericTypeAlias2[T1, T2]" | T1 | T2]

g4: GenericTypeAlias2[str, int] = [[3, ["hi"]], "hi"]  # OK
g5: GenericTypeAlias2[str, float] = [[3, ["hi", 3.4, [3.4]]], "hi"]  # OK
g6: GenericTypeAlias2[str, int] = [[3, ["hi", 3, [3.4]]], "hi"]  # E


RecursiveUnion: TypeAlias = Union["RecursiveUnion", int]  # E: cyclical reference

# On one line because different type checkers report the error on different lines
MutualReference1: TypeAlias = Union["MutualReference2", int]; MutualReference2: TypeAlias = Union["MutualReference1", str]  # E: cyclical reference
