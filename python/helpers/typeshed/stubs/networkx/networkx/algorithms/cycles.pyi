from _typeshed import Incomplete
from collections.abc import Generator

from networkx.classes.digraph import DiGraph
from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = [
    "cycle_basis",
    "simple_cycles",
    "recursive_simple_cycles",
    "find_cycle",
    "minimum_cycle_basis",
    "chordless_cycles",
    "girth",
]

@_dispatchable
def cycle_basis(G: Graph[_Node], root: _Node | None = None): ...
@_dispatchable
def simple_cycles(G: Graph[_Node], length_bound: int | None = None) -> Generator[Incomplete, Incomplete, None]: ...

class _NeighborhoodCache(dict[Incomplete, Incomplete]):
    G: Incomplete

    def __init__(self, G) -> None: ...
    def __missing__(self, v): ...

@_dispatchable
def chordless_cycles(G: DiGraph[_Node], length_bound: int | None = None) -> Generator[Incomplete, Incomplete, None]: ...
@_dispatchable
def recursive_simple_cycles(G: DiGraph[_Node]): ...
@_dispatchable
def find_cycle(G: Graph[_Node], source=None, orientation=None): ...
@_dispatchable
def minimum_cycle_basis(G: Graph[_Node], weight: str | None = None): ...
@_dispatchable
def girth(G) -> float | int: ...
