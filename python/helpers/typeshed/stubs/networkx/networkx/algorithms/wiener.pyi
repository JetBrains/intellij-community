from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["wiener_index", "schultz_index", "gutman_index"]

@_dispatchable
def wiener_index(G: Graph[_Node], weight: str | None = None) -> float: ...
@_dispatchable
def schultz_index(G, weight=None) -> float: ...
@_dispatchable
def gutman_index(G, weight=None) -> float: ...
