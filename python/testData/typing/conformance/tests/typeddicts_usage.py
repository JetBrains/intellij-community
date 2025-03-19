"""
Tests for basic usage of TypedDict.
"""

from typing import TypeVar, TypedDict


class Movie(TypedDict):
    name: str
    year: int


movie: Movie = {"name": "Blade Runner", "year": 1982}


def record_movie(movie: Movie) -> None:
    ...


record_movie({"name": "Blade Runner", "year": 1982})


movie["director"] = "Ridley Scott"  # E: invalid key 'director'
movie["year"] = "1982"  # E: invalid value type ("int" expected)

# The code below should be rejected, since 'title' is not a valid key,
# and the 'name' key is missing:
movie2: Movie = {"title": "Blade Runner", "year": 1982}  # E

m = Movie(name='Blade Runner', year=1982)


# > TypedDict type objects cannot be used in isinstance() tests such as
# > isinstance(d, Movie).
if isinstance(movie, Movie):  # E
    pass


# TypedDict should not be allowed as a bound for a TypeVar.
T = TypeVar("T", bound=TypedDict) # E
