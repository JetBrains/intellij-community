from typing import overload
from typing_extensions import deprecated

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["node_link_data", "node_link_graph"]

@overload
@deprecated(
    """\
The `link` argument is deprecated and will be removed in version `3.6`.
Use the `edges` keyword instead."""
)
def node_link_data(
    G: Graph[_Node],
    *,
    source: str = "source",
    target: str = "target",
    name: str = "id",
    key: str = "key",
    edges: str | None = None,
    nodes: str = "nodes",
    link: str | None = None,
): ...
@overload
def node_link_data(
    G: Graph[_Node],
    *,
    source: str = "source",
    target: str = "target",
    name: str = "id",
    key: str = "key",
    edges: str | None = None,
    nodes: str = "nodes",
): ...
@_dispatchable
def node_link_graph(
    data,
    directed: bool = False,
    multigraph: bool = True,
    attrs=None,
    *,
    source: str = "source",
    target: str = "target",
    name: str = "id",
    key: str = "key",
    edges: str | None = None,
    nodes: str = "nodes",
    link: str | None = None,
): ...
