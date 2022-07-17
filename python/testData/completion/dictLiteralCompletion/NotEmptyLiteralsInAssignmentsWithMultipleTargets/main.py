from typing import TypedDict


class Point(TypedDict):
    x: int
    y: int


class NotPoint(TypedDict):
    a: str
    b: str


p1: Point
p2: NotPoint
p1, p2 = {}, {'a': '42', '<caret>'}
