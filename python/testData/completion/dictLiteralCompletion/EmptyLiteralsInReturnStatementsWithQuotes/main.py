from typing import TypedDict


class Point(TypedDict):
    x: int
    y: int


def create_another_point() -> Point:
    return {'<caret>'}
