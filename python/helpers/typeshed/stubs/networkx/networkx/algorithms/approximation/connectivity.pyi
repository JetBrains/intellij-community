from _typeshed import Incomplete
from collections.abc import Iterable

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["local_node_connectivity", "node_connectivity", "all_pairs_node_connectivity"]

@_dispatchable
def local_node_connectivity(G: Graph[_Node], source: _Node, target: _Node, cutoff: int | None = None): ...
@_dispatchable
def node_connectivity(G: Graph[_Node], s: _Node | None = None, t: _Node | None = None): ...
@_dispatchable
def all_pairs_node_connectivity(G: Graph[_Node], nbunch: Iterable[Incomplete] | None = None, cutoff: int | None = None): ...
