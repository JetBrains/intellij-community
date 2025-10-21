from _typeshed import Incomplete

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable
from numpy.random import RandomState

__all__ = ["kernighan_lin_bisection"]

@_dispatchable
def kernighan_lin_bisection(
    G: Graph[_Node],
    partition: tuple[Incomplete] | None = None,
    max_iter: int = 10,
    weight: str = "weight",
    seed: int | RandomState | None = None,
): ...
