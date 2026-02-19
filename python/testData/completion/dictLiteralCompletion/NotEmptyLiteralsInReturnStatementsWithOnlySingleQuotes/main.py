from typing import TypedDict


class Point(TypedDict):
    x: int
    y: int
    z: int


def create_point() -> Point:
    return {'z': 24, 'y': 42, <caret>}
