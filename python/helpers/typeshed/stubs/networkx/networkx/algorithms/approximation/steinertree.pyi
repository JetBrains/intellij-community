from _typeshed import Incomplete
from collections.abc import Iterable

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["metric_closure", "steiner_tree"]

@_dispatchable
def metric_closure(G: Graph[_Node], weight="weight"): ...
@_dispatchable
def steiner_tree(G: Graph[_Node], terminal_nodes: Iterable[Incomplete], weight: str = "weight", method: str | None = None): ...
