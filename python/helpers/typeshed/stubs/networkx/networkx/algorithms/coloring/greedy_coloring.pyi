from _typeshed import Incomplete
from collections.abc import Callable, Generator
from typing import Final

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = [
    "greedy_color",
    "strategy_connected_sequential",
    "strategy_connected_sequential_bfs",
    "strategy_connected_sequential_dfs",
    "strategy_independent_set",
    "strategy_largest_first",
    "strategy_random_sequential",
    "strategy_saturation_largest_first",
    "strategy_smallest_last",
]

@_dispatchable
def strategy_largest_first(G, colors): ...
@_dispatchable
def strategy_random_sequential(G, colors, seed=None): ...
@_dispatchable
def strategy_smallest_last(G, colors): ...
@_dispatchable
def strategy_independent_set(G, colors) -> Generator[Incomplete, Incomplete, None]: ...
@_dispatchable
def strategy_connected_sequential_bfs(G, colors): ...
@_dispatchable
def strategy_connected_sequential_dfs(G, colors): ...
@_dispatchable
def strategy_connected_sequential(G, colors, traversal: str = "bfs") -> Generator[Incomplete, None, None]: ...
@_dispatchable
def strategy_saturation_largest_first(G, colors) -> Generator[Incomplete, None, Incomplete]: ...

STRATEGIES: Final[dict[str, Callable[..., Incomplete]]]

@_dispatchable
def greedy_color(G: Graph[_Node], strategy="largest_first", interchange: bool = False): ...
