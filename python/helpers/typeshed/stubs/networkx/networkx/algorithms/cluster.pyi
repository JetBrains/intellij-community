from collections.abc import Iterable

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["triangles", "average_clustering", "clustering", "transitivity", "square_clustering", "generalized_degree"]

@_dispatchable
def triangles(G: Graph[_Node], nodes=None): ...
@_dispatchable
def average_clustering(
    G: Graph[_Node], nodes: Iterable[_Node] | None = None, weight: str | None = None, count_zeros: bool = True
): ...
@_dispatchable
def clustering(G: Graph[_Node], nodes=None, weight: str | None = None): ...
@_dispatchable
def transitivity(G: Graph[_Node]): ...
@_dispatchable
def square_clustering(G: Graph[_Node], nodes: Iterable[_Node] | None = None): ...
@_dispatchable
def generalized_degree(G: Graph[_Node], nodes: Iterable[_Node] | None = None): ...
