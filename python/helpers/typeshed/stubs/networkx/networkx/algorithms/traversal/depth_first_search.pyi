from _typeshed import Incomplete
from collections.abc import Callable, Generator

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = [
    "dfs_edges",
    "dfs_tree",
    "dfs_predecessors",
    "dfs_successors",
    "dfs_preorder_nodes",
    "dfs_postorder_nodes",
    "dfs_labeled_edges",
]

@_dispatchable
def dfs_edges(
    G: Graph[_Node], source: _Node | None = None, depth_limit=None, *, sort_neighbors: Callable[..., Incomplete] | None = None
) -> Generator[tuple[_Node, _Node], None, None]: ...
@_dispatchable
def dfs_tree(
    G: Graph[_Node], source: _Node | None = None, depth_limit=None, *, sort_neighbors: Callable[..., Incomplete] | None = None
): ...
@_dispatchable
def dfs_predecessors(
    G: Graph[_Node], source: _Node | None = None, depth_limit=None, *, sort_neighbors: Callable[..., Incomplete] | None = None
) -> dict[Incomplete, Incomplete]: ...
@_dispatchable
def dfs_successors(
    G: Graph[_Node], source: _Node | None = None, depth_limit=None, *, sort_neighbors: Callable[..., Incomplete] | None = None
) -> dict[Incomplete, list[Incomplete]]: ...
@_dispatchable
def dfs_postorder_nodes(
    G: Graph[_Node], source: _Node | None = None, depth_limit=None, *, sort_neighbors: Callable[..., Incomplete] | None = None
): ...
@_dispatchable
def dfs_preorder_nodes(
    G: Graph[_Node], source: _Node | None = None, depth_limit=None, *, sort_neighbors: Callable[..., Incomplete] | None = None
): ...
@_dispatchable
def dfs_labeled_edges(
    G: Graph[_Node], source: _Node | None = None, depth_limit=None, *, sort_neighbors: Callable[..., Incomplete] | None = None
) -> None: ...
