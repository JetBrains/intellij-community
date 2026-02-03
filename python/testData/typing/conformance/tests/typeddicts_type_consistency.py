"""
Tests the type consistency rules for TypedDict objects.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/typeddict.html#type-consistency

from typing import Any, Literal, Mapping, TypedDict


class A1(TypedDict):
    x: int | None


class B1(TypedDict):
    x: int


b1: B1 = {"x": 0}

# > Value types behave invariantly.
a1: A1 = b1  # E: 'B1' not compatible with 'A1'

# > any TypedDict type is consistent with Mapping[str, object]
v1: Mapping[str, object] = b1

# > A TypedDict type with a required key is not consistent with a TypedDict
# > type where the same key is a non-required key.

class A2(TypedDict, total=False):
    x: int


class B2(TypedDict):
    x: int


b2: B2 = {"x": 0}
a2: A2 = b2  # E: 'B2' not compatible with 'A2'


# > A TypedDict type A is consistent with TypedDict B if A is structurally
# > compatible with B. This is true if and only if both of these conditions
# > are satisfied:
# > 1. For each key in B, A has the corresponding key and the corresponding
# >   value type in A is consistent with the value type in B. For each key in B,
# >   the value type in B is also consistent with the corresponding value type
# >   in A.
# > 2. For each required key in B, the corresponding key is required in A. For
# >   each non-required key in B, the corresponding key is not required in A.


class A3(TypedDict):
    x: int


class B3(TypedDict):
    x: int
    y: int


b3: B3 = {"x": 0, "y": 0}
a3: A3 = b3

a3 = {"x": 0}
b3 = a3  # E


# This should generate an error because it's a literal assignment.
a3_1: A3 = {"x": 0, "y": 0}  # E

# This should not generate an error.
a3_2 = b3

# > A TypedDict isn’t consistent with any Dict[...] type.

d1: dict[str, int] = b3  # E
d2: dict[str, object] = b3  # E
d3: dict[Any, Any] = b3  # E

# > A TypedDict with all int values is not consistent with Mapping[str, int].

m1: Mapping[str, int] = b3  # E
m2: Mapping[str, object] = b3  # OK
m3: Mapping[str, Any] = b3  # OK


# Test "get" method.
UserType1 = TypedDict("UserType1", {"name": str, "age": int}, total=False)
user1: UserType1 = {"name": "Bob", "age": 40}

name1: str = user1.get("name", "n/a")
age1: int = user1.get("age", 42)

UserType2 = TypedDict("UserType2", {"name": str, "age": int})
user2: UserType2 = {"name": "Bob", "age": 40}

name2: str | None = user2.get("name")

# The spec does not say whether type checkers should adjust the return type of `.get()`
# to exclude `None` if it is known that the key exists. Either option is acceptable.
name3: str = user2.get("name")  # E?

age2: int = user2.get("age", 42)

age3: int | str = user2.get("age", "42")

age4: int = user2.get("age", "42")  # E?

# Test nested TypedDicts.
class Inner1(TypedDict):
    inner_key: str


class Inner2(TypedDict):
    inner_key: Inner1


class Outer1(TypedDict):
    outer_key: Inner2


o1: Outer1 = {"outer_key": {"inner_key": {"inner_key": "hi"}}}

# This should generate an error because the inner-most value
# should be a string.
o2: Outer1 = {"outer_key": {"inner_key": {"inner_key": 1}}}  # E


class Inner3(TypedDict):
    x: int


class Inner4(TypedDict):
    x: int


class Outer2(TypedDict):
    y: str
    z: Literal[""] | Inner3


class Outer3(TypedDict):
    y: str
    z: Literal[""] | Inner4


def func1(td: Outer3):
    ...


o3: Outer2 = {"y": "", "z": {"x": 0}}
o4: Outer3 = o3
