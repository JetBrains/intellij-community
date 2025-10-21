from typing import Any

from networkx.utils.backends import _dispatchable

__all__ = ["cytoscape_data", "cytoscape_graph"]

# Any: Complex type union
def cytoscape_data(G, name: str = "name", ident: str = "id") -> dict[str, Any]: ...
@_dispatchable
def cytoscape_graph(data, name: str = "name", ident: str = "id"): ...
