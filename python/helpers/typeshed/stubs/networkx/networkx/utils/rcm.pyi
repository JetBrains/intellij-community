from _typeshed import Incomplete
from collections.abc import Callable, Generator

from networkx.classes.graph import Graph, _Node

__all__ = ["cuthill_mckee_ordering", "reverse_cuthill_mckee_ordering"]

def cuthill_mckee_ordering(
    G: Graph[_Node], heuristic: Callable[..., Incomplete] | None = None
) -> Generator[Incomplete, Incomplete]: ...
def reverse_cuthill_mckee_ordering(
    G: Graph[_Node], heuristic: Callable[..., Incomplete] | None = None
) -> Generator[Incomplete, Incomplete, Incomplete]: ...
def connected_cuthill_mckee_ordering(G: Graph[_Node], heuristic=None): ...
def pseudo_peripheral_node(G: Graph[_Node]): ...
