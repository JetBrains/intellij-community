from _typeshed import Incomplete
from collections.abc import Collection

from networkx.utils.backends import _dispatchable

__all__ = ["modularity_matrix", "directed_modularity_matrix"]

@_dispatchable
def modularity_matrix(G, nodelist: Collection[Incomplete] | None = None, weight=None): ...
@_dispatchable
def directed_modularity_matrix(G, nodelist: Collection[Incomplete] | None = None, weight=None): ...
