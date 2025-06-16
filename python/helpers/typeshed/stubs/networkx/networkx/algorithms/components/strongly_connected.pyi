from collections.abc import Generator

from networkx.classes.digraph import DiGraph
from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = [
    "number_strongly_connected_components",
    "strongly_connected_components",
    "is_strongly_connected",
    "kosaraju_strongly_connected_components",
    "condensation",
]

@_dispatchable
def strongly_connected_components(G: Graph[_Node]) -> Generator[set[_Node], None, None]: ...
@_dispatchable
def kosaraju_strongly_connected_components(G: Graph[_Node], source=None) -> Generator[set[_Node], None, None]: ...
@_dispatchable
def number_strongly_connected_components(G: Graph[_Node]) -> int: ...
@_dispatchable
def is_strongly_connected(G: Graph[_Node]) -> bool: ...
@_dispatchable
def condensation(G: DiGraph[_Node], scc=None) -> DiGraph[int]: ...
