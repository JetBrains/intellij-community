from typing import TypedDict


class Movie(TypedDict):
    name: str
    year: int


class BookBasedMovie(Movie, total=False):
    based_on: str

class ScaryBookBasedMovie(BookBasedMovie):
    rating: float


movie = BookBasedMovie(<arg1>)
movie2 = ScaryBookBasedMovie(<arg2>)