from _typeshed import Incomplete
from collections.abc import Callable, Generator
from typing import Literal

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = [
    "bfs_edges",
    "bfs_tree",
    "bfs_predecessors",
    "bfs_successors",
    "descendants_at_distance",
    "bfs_layers",
    "bfs_labeled_edges",
    "generic_bfs_edges",
]

@_dispatchable
def generic_bfs_edges(G, source, neighbors=None, depth_limit=None) -> Generator[tuple[Incomplete, Incomplete]]: ...
@_dispatchable
def bfs_edges(
    G: Graph[_Node],
    source: _Node,
    reverse: bool | None = False,
    depth_limit=None,
    sort_neighbors: Callable[..., Incomplete] | None = None,
) -> Generator[Incomplete, Incomplete, None]: ...
@_dispatchable
def bfs_tree(
    G: Graph[_Node],
    source: _Node,
    reverse: bool | None = False,
    depth_limit=None,
    sort_neighbors: Callable[..., Incomplete] | None = None,
): ...
@_dispatchable
def bfs_predecessors(
    G: Graph[_Node], source: _Node, depth_limit=None, sort_neighbors: Callable[..., Incomplete] | None = None
) -> Generator[Incomplete, None, None]: ...
@_dispatchable
def bfs_successors(
    G: Graph[_Node], source: _Node, depth_limit=None, sort_neighbors: Callable[..., Incomplete] | None = None
) -> Generator[Incomplete, None, None]: ...
@_dispatchable
def bfs_layers(G: Graph[_Node], sources) -> Generator[Incomplete, None, None]: ...
@_dispatchable
def bfs_labeled_edges(G, sources) -> Generator[tuple[Incomplete, Incomplete, Literal["tree", "level", "forward", "reverse"]]]: ...
@_dispatchable
def descendants_at_distance(G: Graph[_Node], source, distance): ...
