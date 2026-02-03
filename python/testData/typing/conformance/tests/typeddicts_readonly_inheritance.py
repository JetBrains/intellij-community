"""
Tests inheritance rules involving typing.ReadOnly.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/typeddict.html#read-only-items

from typing import Collection, NotRequired, Required, TypedDict
from typing_extensions import ReadOnly

# > Subclasses can redeclare read-only items as non-read-only, allowing them
# > to be mutated.


class NamedDict(TypedDict):
    name: ReadOnly[str]


class Album1(NamedDict):
    name: str
    year: int


a1: Album1 = {"name": "Flood", "year": 1990}
a1["year"] = 1973  # OK
a1["name"] = "Dark Side Of The Moon"  # OK


# > If a read-only item is not redeclared, it remains read-only.


class Album2(NamedDict):
    year: int


a2: Album2 = {"name": "Flood", "year": 1990}
a2["name"] = "Dark Side Of The Moon"  # E


# > Subclasses can narrow value types of read-only items.


class AlbumCollection(TypedDict):
    albums: ReadOnly[Collection[Album1]]
    alt: ReadOnly[list[str | int]]


class RecordShop(AlbumCollection):
    name: str
    albums: ReadOnly[list[Album1]]  # OK
    alt: ReadOnly[list[str]]  # E


# > Subclasses can require items that are read-only but not required in the
# > superclass.


class OptionalName(TypedDict):
    name: ReadOnly[NotRequired[str]]


class RequiredName(OptionalName):
    name: ReadOnly[Required[str]]  # OK


d: RequiredName = {}  # E


# > Subclasses can combine these rules.


class OptionalIdent(TypedDict):
    ident: ReadOnly[NotRequired[str | int]]


class User(OptionalIdent):
    ident: str  # Required, mutable, and not an int


u: User
u = {"ident": ""}  # OK
u["ident"] = ""  # OK
u["ident"] = 3  # E
u = {"ident": 3}  # E
u = {}  # E


class F1(TypedDict):
    a: Required[int]
    b: ReadOnly[NotRequired[int]]
    c: ReadOnly[Required[int]]


class F3(F1):
    a: ReadOnly[int]  # E


class F4(F1):
    a: NotRequired[int]  # E


class F5(F1):
    b: ReadOnly[Required[int]]  # OK


class F6(F1):
    c: ReadOnly[NotRequired[int]]  # E


class TD_A1(TypedDict):
    x: int
    y: ReadOnly[int]


class TD_A2(TypedDict):
    x: float
    y: ReadOnly[float]


class TD_A(TD_A1, TD_A2): ...  # E: x is incompatible


class TD_B1(TypedDict):
    x: ReadOnly[NotRequired[int]]
    y: ReadOnly[Required[int]]


class TD_B2(TypedDict):
    x: ReadOnly[Required[int]]
    y: ReadOnly[NotRequired[int]]


class TD_B(TD_B1, TD_B2): ...  # E: x is incompatible
