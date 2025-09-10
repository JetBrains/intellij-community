from _typeshed import Incomplete

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["clique_removal", "max_clique", "large_clique_size", "maximum_independent_set"]

@_dispatchable
def maximum_independent_set(G: Graph[_Node]) -> set[Incomplete]: ...
@_dispatchable
def max_clique(G: Graph[_Node]) -> set[Incomplete]: ...
@_dispatchable
def clique_removal(G: Graph[_Node]): ...
@_dispatchable
def large_clique_size(G: Graph[_Node]): ...
