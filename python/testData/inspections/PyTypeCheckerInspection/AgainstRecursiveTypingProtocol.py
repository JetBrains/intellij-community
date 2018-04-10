from typing import Generic, Iterable, List, Protocol, TypeVar

T = TypeVar("T")


class Traversable(Protocol):
    def leaves(self) -> Iterable['Traversable']:
        pass


class SimpleTree:
    def leaves(self) -> List['SimpleTree']:
        pass


class Tree(Generic[T]):
    def leaves(self) -> List['Tree[T]']:
        pass


def traverse(t: Traversable):
    for l in t.leaves():
        traverse(l)


traverse(SimpleTree())
traverse(Tree())
