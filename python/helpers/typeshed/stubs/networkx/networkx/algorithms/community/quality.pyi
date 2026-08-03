from _typeshed import Incomplete
from collections.abc import Iterable

from networkx.classes.graph import Graph, _Node
from networkx.exception import NetworkXError
from networkx.utils.backends import _dispatchable
from networkx.utils.decorators import argmap

__all__ = ["modularity", "partition_quality"]

class NotAPartition(NetworkXError):
    def __init__(self, G: Graph[_Node], collection) -> None: ...

require_partition: argmap

@_dispatchable
def intra_community_edges(G: Graph[_Node], partition: Iterable[Incomplete]): ...
@_dispatchable
def inter_community_edges(G: Graph[_Node], partition: Iterable[Incomplete]): ...
@_dispatchable
def inter_community_non_edges(G: Graph[_Node], partition: Iterable[Incomplete]): ...
@_dispatchable
def modularity(
    G: Graph[_Node], communities: Iterable[set[Incomplete]], weight: str | None = "weight", resolution: float = 1
) -> float: ...
@_dispatchable
def partition_quality(G: Graph[_Node], partition: Iterable[Incomplete]) -> tuple[float, float]: ...
