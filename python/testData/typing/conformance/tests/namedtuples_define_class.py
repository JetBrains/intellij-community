"""
Tests NamedTuple definitions using the class syntax.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/namedtuples.html#defining-named-tuples

# > Type checkers should support the class syntax

from typing import Generic, NamedTuple, TypeVar, assert_type


class Point(NamedTuple):
    x: int
    y: int
    units: str = "meters"


# > Type checkers should synthesize a ``__new__`` method based on
# > the named tuple fields.

p1 = Point(1, 2)
assert_type(p1, Point)
assert_type(p1[0], int)
assert_type(p1[1], int)
assert_type(p1[2], str)
assert_type(p1[-1], str)
assert_type(p1[-2], int)
assert_type(p1[-3], int)
assert_type(p1[0:2], tuple[int, int])
assert_type(p1[0:], tuple[int, int, str])

print(p1[3])  # E
print(p1[-4])  # E

p2 = Point(1, 2, "")
assert_type(p2, Point)

p3 = Point(x=1, y=2)
assert_type(p3, Point)

p4 = Point(x=1, y=2, units="")
assert_type(p4, Point)

p5 = Point(1)  # E
p6 = Point(x=1)  # E
p7 = Point(1, "")  # E
p8 = Point(1, 2, units=3)  # E
p9 = Point(1, 2, "", "")  # E
p10 = Point(1, 2, "", other="")  # E


# > The runtime implementation of ``NamedTuple`` enforces that fields with default
# > values must come after fields without default values. Type checkers should
# > likewise enforce this restriction::


class Location(NamedTuple):
    altitude: float = 0.0
    latitude: float  # E: previous field has a default value


# > A named tuple class can be subclassed, but any fields added by the subclass
# > are not considered part of the named tuple type. Type checkers should enforce
# > that these newly-added fields do not conflict with the named tuple fields
# > in the base class.


class PointWithName(Point):
    name: str = ""  # OK


pn1 = PointWithName(1, 2, "")
pnt1: tuple[int, int, str] = pn1
assert_type(pn1.name, str)


class BadPointWithName(Point):
    name: str = ""  # OK
    x: int = 0  # E


# > In Python 3.11 and newer, the class syntax supports generic named tuple classes.
# > Type checkers should support this.

T = TypeVar("T")


class Property(NamedTuple, Generic[T]):
    name: str
    value: T


pr1 = Property("", 3.4)
assert_type(pr1, Property[float])
assert_type(pr1[1], float)
assert_type(pr1.value, float)

Property[str]("", 3.1)  # E


# > ``NamedTuple`` does not support multiple inheritance. Type checkers should
# > enforce this limitation.


class Unit(NamedTuple, object):  # E
    name: str
