from _typeshed import Incomplete
from collections.abc import Iterable

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["triangles", "average_clustering", "clustering", "transitivity", "square_clustering", "generalized_degree"]

@_dispatchable
def triangles(G: Graph[_Node], nodes=None) -> int | dict[Incomplete, int]: ...
@_dispatchable
def average_clustering(
    G: Graph[_Node], nodes: Iterable[_Node] | None = None, weight: str | None = None, count_zeros: bool = True
) -> float: ...
@_dispatchable
def clustering(G: Graph[_Node], nodes=None, weight: str | None = None) -> float | int | dict[Incomplete, float | int]: ...
@_dispatchable
def transitivity(G: Graph[_Node]) -> float: ...
@_dispatchable
def square_clustering(G: Graph[_Node], nodes: Iterable[_Node] | None = None) -> float | int | dict[Incomplete, float | int]: ...
@_dispatchable
def generalized_degree(G: Graph[_Node], nodes: Iterable[_Node] | None = None): ...
