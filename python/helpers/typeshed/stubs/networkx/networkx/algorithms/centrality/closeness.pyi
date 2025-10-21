from _typeshed import Incomplete, SupportsGetItem

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["closeness_centrality", "incremental_closeness_centrality"]

@_dispatchable
def closeness_centrality(
    G: Graph[_Node], u: _Node | None = None, distance=None, wf_improved: bool | None = True
) -> dict[_Node, float]: ...
@_dispatchable
def incremental_closeness_centrality(
    G: Graph[_Node],
    edge: tuple[Incomplete],
    prev_cc: SupportsGetItem[Incomplete, Incomplete] | None = None,
    insertion: bool | None = True,
    wf_improved: bool | None = True,
) -> dict[_Node, float]: ...
