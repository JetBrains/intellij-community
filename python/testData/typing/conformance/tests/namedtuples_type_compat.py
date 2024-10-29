"""
Tests NamedTuple type compatibility rules.
"""

from typing import Any, NamedTuple

# Specification: https://typing.readthedocs.io/en/latest/spec/namedtuples.html#type-compatibility-rules


class Point(NamedTuple):
    x: int
    y: int
    units: str = "meters"


# > A named tuple is a subtype of a ``tuple`` with a known length and parameterized
# > by types corresponding to the named tuple's individual field types.

p = Point(x=1, y=2, units="inches")
v1: tuple[int, int, str] = p  # OK
v2: tuple[Any, ...] = p  # OK
v3: tuple[int, int] = p  # E: too few elements
v4: tuple[int, str, str] = p  # E: incompatible element type

# > As with normal tuples, named tuples are covariant in their type parameters.

v5: tuple[float, float, str] = p  # OK
