from typing import Generic, Protocol, TypeVar

T = TypeVar("T")


class Box1(Protocol[T]):
    attr: T


class Box2(Protocol, Generic[T]):
    attr: T


class BoxImpl(Generic[T]):
    def __init__(self, attr: T) -> None:
        self.attr = attr


def b1(b: Box1[int]):
    print(b.attr)


def b2(b: Box2[int]):
    print(b.attr)


b3: BoxImpl[int]
b1(b3)
b2(b3)
