from typing import ClassVar


def dist(p1):
    print(p1.x)
    print(p1.y)
    return p1


class A:
    x: ClassVar[int]
    y: ClassVar[str]


dist(A)
dist(A())