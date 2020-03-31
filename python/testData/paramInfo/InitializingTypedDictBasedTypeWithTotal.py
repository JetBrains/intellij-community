from typing import TypedDict


class Movie(TypedDict, total=False):
    name: str
    year: int


movie = Movie(<arg1>)


class Movie2(TypedDict, total=True):
    name: str
    year: int


movie2 = Movie2(<arg2>)