from _typeshed import Incomplete
from collections.abc import Iterable

from networkx.classes.graph import Graph
from networkx.utils.backends import _dispatchable

__all__ = ["grid_2d_graph", "grid_graph", "hypercube_graph", "triangular_lattice_graph", "hexagonal_lattice_graph"]

@_dispatchable
def grid_2d_graph(
    m, n, periodic: bool = False, create_using: Graph[Incomplete] | type[Graph[Incomplete]] | None = None
) -> Graph[Incomplete]: ...
@_dispatchable
def grid_graph(dim: list[float] | tuple[float, ...] | Iterable[Incomplete], periodic: bool = False) -> Graph[Incomplete]: ...
@_dispatchable
def hypercube_graph(n: int) -> Graph[Incomplete]: ...
@_dispatchable
def triangular_lattice_graph(
    m: int,
    n: int,
    periodic: bool = False,
    with_positions: bool = True,
    create_using: Graph[Incomplete] | type[Graph[Incomplete]] | None = None,
) -> Graph[Incomplete]: ...
@_dispatchable
def hexagonal_lattice_graph(
    m: int,
    n: int,
    periodic: bool = False,
    with_positions: bool = True,
    create_using: Graph[Incomplete] | type[Graph[Incomplete]] | None = None,
) -> Graph[Incomplete]: ...
