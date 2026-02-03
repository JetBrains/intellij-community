"""
Tests type consistency rules for TypedDict with ReadOnly items.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/typeddict.html#read-only-items

from typing import Any, Collection, NotRequired, Required, TypedDict
from typing_extensions import ReadOnly

# > A TypedDict type ``A`` is consistent with TypedDict ``B`` if ``A`` is
# > structurally compatible with ``B``. This is true if and only if all of
# > the following are satisfied:

# > For each item in ``B``, ``A`` has the corresponding key, unless the item
# > in ``B`` is read-only, not required, and of top value type
# > (``ReadOnly[NotRequired[object]]``).


class A1(TypedDict):
    x: Required[int]


class B1(TypedDict):
    x: Required[int]
    y: NotRequired[str]


class C1(TypedDict):
    x: Required[int]
    y: ReadOnly[NotRequired[str]]


def func1(a: A1, b: B1, c: C1):
    v1: A1 = b  # OK
    v2: A1 = c  # OK

    v3: B1 = a  # E
    v4: B1 = c  # E

    v5: C1 = a  # E
    v6: C1 = b  # OK


# > For each item in ``B``, if ``A`` has the corresponding key, the
# > corresponding value type in ``A`` is consistent with the value type in ``B``.

# (This is the same rule that applies to TypedDicts without read-only items,
# so we will skip this redundant test.)

# > For each non-read-only item in ``B``, its value type is consistent with
# > the corresponding value type in ``A``.

# (This is the same rule that applies to TypedDicts without read-only items,
# so we will skip this redundant test.)

# > For each required key in ``B``, the corresponding key is required in ``A``.

# (This is the same rule that applies to TypedDicts without read-only items,
# so we will skip this redundant test.)

# > For each non-required key in ``B``, if the item is not read-only in ``B``,
# > the corresponding key is not required in ``A``.


class A2(TypedDict):
    x: NotRequired[ReadOnly[str]]


class B2(TypedDict):
    x: NotRequired[str]


class C2(TypedDict):
    x: Required[str]


def func2(a: A2, b: B2, c: C2):
    v1: A2 = b  # OK
    v2: A2 = c  # OK

    v3: B2 = a  # E
    v4: B2 = c  # E

    v5: C2 = a  # E
    v6: C2 = b  # E
