from _typeshed import Incomplete
from collections.abc import Callable, Generator
from typing import overload

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = [
    "shortest_path",
    "all_shortest_paths",
    "single_source_all_shortest_paths",
    "all_pairs_all_shortest_paths",
    "shortest_path_length",
    "average_shortest_path_length",
    "has_path",
]

@_dispatchable
def has_path(G: Graph[_Node], source: _Node, target: _Node) -> bool: ...
@overload
def shortest_path(
    G: Graph[_Node],
    source: _Node | None = None,
    target: _Node | None = None,
    weight: str | Callable[..., Incomplete] | None = None,
    method: str | None = "dijkstra",
) -> list[_Node]: ...
@overload
def shortest_path(
    G: Graph[_Node],
    source: _Node | None = None,
    target: _Node | None = None,
    weight: str | Callable[..., Incomplete] | None = None,
    method: str | None = "dijkstra",
) -> dict[_Node, list[_Node]]: ...
@overload
def shortest_path(
    G: Graph[_Node],
    source: _Node | None = None,
    target: _Node | None = None,
    weight: str | Callable[..., Incomplete] | None = None,
    method: str | None = "dijkstra",
) -> dict[_Node, list[_Node]]: ...
@_dispatchable
def shortest_path_length(
    G: Graph[_Node],
    source: _Node | None = None,
    target: _Node | None = None,
    weight: str | Callable[..., Incomplete] | None = None,
    method: str | None = "dijkstra",
): ...
@_dispatchable
def average_shortest_path_length(
    G: Graph[_Node], weight: str | Callable[..., Incomplete] | None = None, method: str | None = None
): ...
@_dispatchable
def all_shortest_paths(
    G: Graph[_Node],
    source: _Node,
    target: _Node,
    weight: str | Callable[..., Incomplete] | None = None,
    method: str | None = "dijkstra",
) -> Generator[list[_Node], None, None]: ...
@_dispatchable
def single_source_all_shortest_paths(
    G, source, weight=None, method="dijkstra"
) -> Generator[tuple[Incomplete, list[list[Incomplete]]]]: ...
@_dispatchable
def all_pairs_all_shortest_paths(
    G, weight=None, method="dijkstra"
) -> Generator[tuple[Incomplete, dict[Incomplete, Incomplete]]]: ...
