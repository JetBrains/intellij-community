from typing import TypedDict


class Point(TypedDict):
    coordinateX: int
    coordinateY: int
    coordinateZ: int


p: Point = {'coo<caret>': 42, 'coordinateY': 42}
