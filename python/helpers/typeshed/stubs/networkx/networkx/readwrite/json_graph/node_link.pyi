from networkx.utils.backends import _dispatchable

__all__ = ["node_link_data", "node_link_graph"]

def node_link_data(
    G,
    *,
    source: str = "source",
    target: str = "target",
    name: str = "id",
    key: str = "key",
    edges: str | None = None,
    nodes: str = "nodes",
    link: str | None = None,
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
