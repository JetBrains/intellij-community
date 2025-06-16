from _typeshed import Incomplete
from collections.abc import Generator

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["k_edge_components", "k_edge_subgraphs", "bridge_components", "EdgeComponentAuxGraph"]

@_dispatchable
def k_edge_components(G: Graph[_Node], k: int): ...
@_dispatchable
def k_edge_subgraphs(G: Graph[_Node], k: int): ...
@_dispatchable
def bridge_components(G: Graph[_Node]) -> Generator[Incomplete, Incomplete, None]: ...

class EdgeComponentAuxGraph:
    A: Incomplete
    H: Incomplete

    @classmethod
    def construct(cls, G): ...
    def k_edge_components(self, k: int) -> Generator[Incomplete, Incomplete, None]: ...
    def k_edge_subgraphs(self, k: int) -> Generator[Incomplete, Incomplete, None]: ...

@_dispatchable
def general_k_edge_subgraphs(G: Graph[_Node], k): ...
