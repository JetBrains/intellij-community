from _typeshed import Incomplete
from collections.abc import Collection

from networkx.classes.digraph import DiGraph
from networkx.classes.graph import Graph
from networkx.utils.backends import _dispatchable

__all__ = [
    "caveman_graph",
    "connected_caveman_graph",
    "relaxed_caveman_graph",
    "random_partition_graph",
    "planted_partition_graph",
    "gaussian_random_partition_graph",
    "ring_of_cliques",
    "windmill_graph",
    "stochastic_block_model",
    "LFR_benchmark_graph",
]

@_dispatchable
def caveman_graph(l: int, k: int) -> Graph[Incomplete]: ...
@_dispatchable
def connected_caveman_graph(l: int, k: int) -> Graph[Incomplete]: ...
@_dispatchable
def relaxed_caveman_graph(l: int, k: int, p: float, seed=None) -> Graph[Incomplete]: ...
@_dispatchable
def random_partition_graph(
    sizes: list[int], p_in: float, p_out: float, seed=None, directed: bool = False
) -> DiGraph[Incomplete]: ...
@_dispatchable
def planted_partition_graph(
    l: int, k: int, p_in: float, p_out: float, seed=None, directed: bool = False
) -> DiGraph[Incomplete]: ...
@_dispatchable
def gaussian_random_partition_graph(
    n: int, s: float, v: float, p_in: float, p_out: float, directed: bool = False, seed=None
) -> DiGraph[Incomplete]: ...
@_dispatchable
def ring_of_cliques(num_cliques: int, clique_size: int) -> Graph[Incomplete]: ...
@_dispatchable
def windmill_graph(n: int, k: int) -> Graph[Incomplete]: ...
@_dispatchable
def stochastic_block_model(
    sizes: list[int],
    p: list[list[float]],
    nodelist: Collection[Incomplete] | None = None,
    seed=None,
    directed: bool = False,
    selfloops: bool = False,
    sparse: bool = True,
) -> DiGraph[Incomplete]: ...
@_dispatchable
def LFR_benchmark_graph(
    n: int,
    tau1: float,
    tau2: float,
    mu: float,
    average_degree: float | None = None,
    min_degree: int | None = None,
    max_degree: int | None = None,
    min_community: int | None = None,
    max_community: int | None = None,
    tol: float = 1e-07,
    max_iters: int = 500,
    seed=None,
) -> Graph[Incomplete]: ...
