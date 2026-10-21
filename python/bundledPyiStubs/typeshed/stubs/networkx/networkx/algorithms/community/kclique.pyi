from _typeshed import Incomplete
from collections.abc import Generator, Iterable

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["k_clique_communities"]

@_dispatchable
def k_clique_communities(G: Graph[_Node], k: int, cliques: Iterable[Incomplete] | None = None) -> Generator[Incomplete]: ...
