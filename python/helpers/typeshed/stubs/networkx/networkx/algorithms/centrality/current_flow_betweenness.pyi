from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable
from numpy.random import RandomState

__all__ = [
    "current_flow_betweenness_centrality",
    "approximate_current_flow_betweenness_centrality",
    "edge_current_flow_betweenness_centrality",
]

@_dispatchable
def approximate_current_flow_betweenness_centrality(
    G: Graph[_Node],
    normalized: bool | None = True,
    weight: str | None = None,
    dtype: type = ...,
    solver: str = "full",
    epsilon: float = 0.5,
    kmax: int = 10000,
    seed: int | RandomState | None = None,
): ...
@_dispatchable
def current_flow_betweenness_centrality(
    G: Graph[_Node], normalized: bool | None = True, weight: str | None = None, dtype: type = ..., solver: str = "full"
): ...
@_dispatchable
def edge_current_flow_betweenness_centrality(
    G: Graph[_Node], normalized: bool | None = True, weight: str | None = None, dtype: type = ..., solver: str = "full"
): ...
