from typing import TypedDict


class Point(TypedDict):
    x: int
    y: int


p: Point = {<caret>}
