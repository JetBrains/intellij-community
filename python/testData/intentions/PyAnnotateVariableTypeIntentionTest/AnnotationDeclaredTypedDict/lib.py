from typing import TypedDict


class Movie(TypedDict):
    name: str
    year: int


def blockbuster() -> Movie:
    ...
