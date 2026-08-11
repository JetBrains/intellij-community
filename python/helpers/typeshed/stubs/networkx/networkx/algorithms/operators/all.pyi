from _typeshed import Incomplete
from collections.abc import Iterable

from networkx.classes.graph import Graph
from networkx.utils.backends import _dispatchable

__all__ = ["union_all", "compose_all", "disjoint_union_all", "intersection_all"]

@_dispatchable
def union_all(graphs: Iterable[Incomplete], rename: Iterable[Incomplete] | None = ()) -> Graph[Incomplete]: ...
@_dispatchable
def disjoint_union_all(graphs: Iterable[Incomplete]) -> Graph[Incomplete]: ...
@_dispatchable
def compose_all(graphs: Iterable[Incomplete]) -> Graph[Incomplete]: ...
@_dispatchable
def intersection_all(graphs: Iterable[Incomplete]) -> Graph[Incomplete]: ...
