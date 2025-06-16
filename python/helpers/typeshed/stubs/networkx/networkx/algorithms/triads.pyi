from _typeshed import Incomplete
from collections.abc import Collection, Generator
from typing import Final

from networkx.classes.digraph import DiGraph
from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["triadic_census", "is_triad", "all_triads", "triads_by_type", "triad_type"]

TRICODES: Final[tuple[int, ...]]
TRIAD_NAMES: Final[tuple[str, ...]]
TRICODE_TO_NAME: Final[dict[int, str]]

@_dispatchable
def triadic_census(G: DiGraph[_Node], nodelist: Collection[_Node] | None = None): ...
@_dispatchable
def is_triad(G: Graph[_Node]) -> bool: ...
@_dispatchable
def all_triads(G: DiGraph[_Node]) -> Generator[Incomplete, None, None]: ...
@_dispatchable
def triads_by_type(G: DiGraph[_Node]): ...
@_dispatchable
def triad_type(G: DiGraph[_Node]): ...
