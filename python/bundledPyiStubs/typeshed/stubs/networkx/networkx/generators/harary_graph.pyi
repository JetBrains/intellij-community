from _typeshed import Incomplete

from networkx.classes.graph import Graph
from networkx.utils.backends import _dispatchable

__all__ = ["hnm_harary_graph", "hkn_harary_graph"]

@_dispatchable
def hnm_harary_graph(
    n: int, m: int, create_using: Graph[Incomplete] | type[Graph[Incomplete]] | None = None
) -> Graph[Incomplete]: ...
@_dispatchable
def hkn_harary_graph(
    k: int, n: int, create_using: Graph[Incomplete] | type[Graph[Incomplete]] | None = None
) -> Graph[Incomplete]: ...
