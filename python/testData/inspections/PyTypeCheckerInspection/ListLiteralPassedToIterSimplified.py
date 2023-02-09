from typing import Generic, Protocol, TypeVar

T = TypeVar('T', covariant=True)
T2 = TypeVar('T2')
T3 = TypeVar('T3')


class SupportsIter(Protocol[T]):
    def __iter__(self) -> T:
        pass


def my_iter(x: SupportsIter[T2]) -> T2:
    pass


class MyList(Generic[T3]):
    def __init__(self, x: T3):
        pass

    def __iter__(self) -> list[int]:
        pass


x = MyList('foo')
my_iter(x)