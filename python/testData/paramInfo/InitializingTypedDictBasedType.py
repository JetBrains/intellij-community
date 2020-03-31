from typing import TypedDict


class Movie(TypedDict):
    name: str
    year: int


movie = Movie(<arg1>)


class Empty(TypedDict):
    pass


empty = Empty(<arg2>)