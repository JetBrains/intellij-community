from typing import TypedDict


class Point(TypedDict):
    x: int
    y: int


def is_even(x: Point) -> bool:
    pass


is_even({'x': 42, <caret>})
