from _typeshed import Incomplete
from collections.abc import Generator

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["is_distance_regular", "is_strongly_regular", "intersection_array", "global_parameters"]

@_dispatchable
def is_distance_regular(G: Graph[_Node]) -> bool: ...
@_dispatchable
def global_parameters(b, c) -> Generator[tuple[Incomplete, Incomplete, Incomplete]]: ...
@_dispatchable
def intersection_array(G: Graph[_Node]): ...
@_dispatchable
def is_strongly_regular(G: Graph[_Node]) -> bool: ...
