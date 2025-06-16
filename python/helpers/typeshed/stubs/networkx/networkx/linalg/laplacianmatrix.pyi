from _typeshed import Incomplete
from collections.abc import Collection

from networkx.utils.backends import _dispatchable

__all__ = [
    "laplacian_matrix",
    "normalized_laplacian_matrix",
    "directed_laplacian_matrix",
    "directed_combinatorial_laplacian_matrix",
]

@_dispatchable
def laplacian_matrix(G, nodelist: Collection[Incomplete] | None = None, weight: str = "weight"): ...
@_dispatchable
def normalized_laplacian_matrix(G, nodelist: Collection[Incomplete] | None = None, weight: str = "weight"): ...
@_dispatchable
def directed_laplacian_matrix(
    G, nodelist: Collection[Incomplete] | None = None, weight: str = "weight", walk_type=None, alpha: float = 0.95
): ...
@_dispatchable
def directed_combinatorial_laplacian_matrix(
    G, nodelist: Collection[Incomplete] | None = None, weight: str = "weight", walk_type=None, alpha: float = 0.95
): ...
