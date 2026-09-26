from _typeshed import Incomplete

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["node_link_data", "node_link_graph"]

def node_link_data(
    G: Graph[_Node],
    *,
    source: str = "source",
    target: str = "target",
    name: str = "id",
    key: str = "key",
    edges: str = "edges",
    nodes: str = "nodes",
) -> dict[Incomplete, Incomplete]: ...
@_dispatchable
def node_link_graph(
    data: dict[Incomplete, Incomplete],
    directed: bool = False,
    multigraph: bool = True,
    attrs=None,
    *,
    source: str = "source",
    target: str = "target",
    name: str = "id",
    key: str = "key",
    edges: str = "edges",
    nodes: str = "nodes",
) -> Graph[Incomplete]: ...
