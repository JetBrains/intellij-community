from _typeshed import Incomplete
from collections.abc import Callable, Generator

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["bfs_beam_edges"]

@_dispatchable
def bfs_beam_edges(
    G: Graph[_Node], source: _Node, value: Callable[..., Incomplete], width: int | None = None
) -> Generator[Incomplete, Incomplete, Incomplete]: ...
