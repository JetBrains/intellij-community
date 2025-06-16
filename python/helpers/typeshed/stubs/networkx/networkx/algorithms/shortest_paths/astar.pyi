from _typeshed import Incomplete, SupportsGetItem
from collections.abc import Callable
from typing import Any

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["astar_path", "astar_path_length"]

@_dispatchable
def astar_path(
    G: Graph[_Node],
    source: _Node,
    target: _Node,
    heuristic: Callable[..., Incomplete] | None = None,
    weight: str | Callable[[Any, Any, SupportsGetItem[str, Any]], float | None] | None = "weight",
    *,
    cutoff: float | None = None,
): ...
@_dispatchable
def astar_path_length(
    G: Graph[_Node],
    source: _Node,
    target: _Node,
    heuristic: Callable[..., Incomplete] | None = None,
    weight: str | Callable[[Any, Any, SupportsGetItem[str, Any]], float | None] | None = "weight",
    *,
    cutoff: float | None = None,
): ...
