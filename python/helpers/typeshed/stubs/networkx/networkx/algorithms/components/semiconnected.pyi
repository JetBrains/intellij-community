from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["is_semiconnected"]

@_dispatchable
def is_semiconnected(G: Graph[_Node]) -> bool: ...
