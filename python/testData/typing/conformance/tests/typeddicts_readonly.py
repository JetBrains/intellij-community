"""
Tests typing.ReadOnly qualifier introduced in PEP 705.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/typeddict.html#read-only-items

from typing import Annotated, NotRequired, Required, TypedDict
from typing_extensions import ReadOnly


# > The ``typing.ReadOnly`` type qualifier is used to indicate that an item
# > declared in a ``TypedDict`` definition may not be mutated (added, modified,
# > or removed).


class Band(TypedDict):
    name: str
    members: ReadOnly[list[str]]


b1: Band = {"name": "blur", "members": []}

b1["name"] = "Blur"  # OK
b1["members"] = ["Damon Albarn"]  # E
b1["members"].append("Damon Albarn")  # OK


# > The :pep:`alternative functional syntax <589#alternative-syntax>` for
# > TypedDict also supports the new type qualifier.

Band2 = TypedDict("Band2", {"name": str, "members": ReadOnly[list[str]]})

b2: Band2 = {"name": "blur", "members": []}

b2["name"] = "Blur"  # OK
b2["members"] = ["Damon Albarn"]  # E
b2["members"].append("Damon Albarn")  # OK


# > ``ReadOnly[]`` can be used with ``Required[]``, ``NotRequired[]`` and
# > ``Annotated[]``, in any nesting order.


class Movie1(TypedDict):
    title: ReadOnly[Required[str]]  # OK
    year: ReadOnly[NotRequired[Annotated[int, ""]]]  # OK


m1: Movie1 = {"title": "", "year": 1991}
m1["title"] = ""  # E
m1["year"] = 1992  # E


class Movie2(TypedDict):
    title: Required[ReadOnly[str]]  # OK
    year: Annotated[NotRequired[ReadOnly[int]], ""]  # OK


m2: Movie2 = {"title": "", "year": 1991}
m2["title"] = ""  # E
m2["year"] = 1992  # E
