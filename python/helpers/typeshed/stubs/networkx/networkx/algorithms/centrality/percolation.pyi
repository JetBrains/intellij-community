from _typeshed import Incomplete, SupportsGetItem

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["percolation_centrality"]

@_dispatchable
def percolation_centrality(
    G: Graph[_Node],
    attribute: str | None = "percolation",
    states: SupportsGetItem[Incomplete, Incomplete] | None = None,
    weight: str | None = None,
): ...
