from _typeshed import Incomplete
from collections.abc import Generator

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["biconnected_components", "biconnected_component_edges", "is_biconnected", "articulation_points"]

@_dispatchable
def is_biconnected(G: Graph[_Node]) -> bool: ...
@_dispatchable
def biconnected_component_edges(G: Graph[_Node]) -> Generator[Incomplete, Incomplete, None]: ...
@_dispatchable
def biconnected_components(G: Graph[_Node]) -> Generator[Incomplete, None, None]: ...
@_dispatchable
def articulation_points(G: Graph[_Node]) -> Generator[Incomplete, None, None]: ...
