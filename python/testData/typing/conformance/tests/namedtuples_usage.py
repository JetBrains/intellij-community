"""
Tests NamedTuple usage.
"""

from typing import NamedTuple, assert_type

# Specification: https://typing.readthedocs.io/en/latest/spec/namedtuples.html#named-tuple-usage


class Point(NamedTuple):
    x: int
    y: int
    units: str = "meters"


# > The fields within a named tuple instance can be accessed by name using an
# > attribute access (``.``) operator. Type checkers should support this.

p = Point(1, 2)
assert_type(p.x, int)
assert_type(p.units, str)


# > Like normal tuples, elements of a named tuple can also be accessed by index,
# > and type checkers should support this.

assert_type(p[0], int)
assert_type(p[1], int)
assert_type(p[2], str)
assert_type(p[-1], str)
assert_type(p[-2], int)
assert_type(p[-3], int)

print(p[3])  # E
print(p[-4])  # E

# > Type checkers should enforce that named tuple fields cannot be overwritten
# > or deleted.

p.x = 3  # E
p[0] = 3  # E
del p.x  # E
del p[0]  # E

# > Like regular tuples, named tuples can be unpacked. Type checkers should understand
# > this.

x1, y1, units1 = p
assert_type(x1, int)
assert_type(units1, str)

x2, y2 = p  # E: too few values to unpack
x3, y3, unit3, other = p  # E: too many values to unpack


class PointWithName(Point):
    name: str = ""  # OK


pn = PointWithName(1, 1)
x4, y4, units4 = pn
