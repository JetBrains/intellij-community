from typing import TypedDict


class Point(TypedDict):
    x: int
    y: int


def create_point() -> Point:
    return {<caret>}
