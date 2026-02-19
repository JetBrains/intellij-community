from typing import TypedDict


class Point(TypedDict):
    x: int
    y: int
    z: int


def create_another_point() -> Point:
    return {'y': 42, """z""": 42, <caret>}
