import typing


def dist(p1):
    print(p1.x)
    print(p1.y)
    return p1


class TP(typing.NamedTuple):
    x: int
    y: int


dist(TP)
dist(TP(1, 2))