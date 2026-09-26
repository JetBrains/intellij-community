"""
Tests the use of Final values when used with TypedDicts.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/typeddict.html#use-of-final-values-and-literal-types

from typing import Final, Literal, TypedDict


class Movie(TypedDict):
    name: str
    year: int


# > Type checkers should allow final names (PEP 591) with string values to be
# > used instead of string literals in operations on TypedDict objects.
YEAR: Final = "year"

m: Movie = {"name": "Alien", "year": 1979}
years_since_epoch = m[YEAR] - 1970


# > An expression with a suitable literal type (PEP 586) can be used instead of
# > a literal value.
def get_value(movie: Movie, key: Literal["year", "name"]) -> int | str:
    return movie[key]
