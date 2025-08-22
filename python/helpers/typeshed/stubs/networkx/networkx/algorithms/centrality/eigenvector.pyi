from _typeshed import Incomplete, SupportsGetItem

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["eigenvector_centrality", "eigenvector_centrality_numpy"]

@_dispatchable
def eigenvector_centrality(
    G: Graph[_Node],
    max_iter: int | None = 100,
    tol: float | None = 1e-06,
    nstart: SupportsGetItem[Incomplete, Incomplete] | None = None,
    weight: str | None = None,
) -> dict[Incomplete, float]: ...
@_dispatchable
def eigenvector_centrality_numpy(
    G: Graph[_Node], weight: str | None = None, max_iter: int | None = 50, tol: float | None = 0
) -> dict[Incomplete, Incomplete]: ...
