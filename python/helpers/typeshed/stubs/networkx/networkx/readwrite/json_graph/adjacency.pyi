from typing import Any

from networkx.utils.backends import _dispatchable

__all__ = ["adjacency_data", "adjacency_graph"]

# Any: Complex type union
def adjacency_data(G, attrs={"id": "id", "key": "key"}) -> dict[str, Any]: ...
@_dispatchable
def adjacency_graph(data, directed: bool = False, multigraph: bool = True, attrs={"id": "id", "key": "key"}): ...
