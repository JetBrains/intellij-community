from typing import TypedDict


class Movie(TypedDict):
    name: str


movie = Movie(name="Joker")
movie.get(<arg1>)