"""
Tests inheritance rules for TypedDict classes.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/typeddict.html#typeddict

from typing import TypedDict


class Movie(TypedDict):
    name: str
    year: int

class BookBasedMovie(Movie):
    based_on: str

class BookBasedMovieAlso(TypedDict):
    name: str
    year: int
    based_on: str

b1: BookBasedMovie = {"name": "Little Women", "year": 2019, "based_on": "Little Women"}

b2: BookBasedMovieAlso = b1


class X(TypedDict):
    x: int

class Y(TypedDict):
    y: str

class XYZ(X, Y):
    z: bool

x1 = XYZ(x=1, y="", z=True)

# > A TypedDict cannot inherit from both a TypedDict type and a
# > non-TypedDict base class other than Generic.

class NonTypedDict:
    pass

class BadTypedDict(TypedDict, NonTypedDict):  # E
    pass


# > Changing a field type of a parent TypedDict class in a subclass is
# > not allowed.

class X1(TypedDict):
   x: str

class Y1(X1):  # E[Y1]
   x: int  # E[Y1]: cannot overwrite TypedDict field "x"


# > Multiple inheritance does not allow conflict types for the same name field:
class X2(TypedDict):
   x: int

class Y2(TypedDict):
   x: str

class XYZ2(X2, Y2):  # E: cannot overwrite TypedDict field "x" while merging
   xyz: bool
