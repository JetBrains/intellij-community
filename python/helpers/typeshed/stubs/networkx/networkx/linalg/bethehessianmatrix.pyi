from _typeshed import Incomplete
from collections.abc import Collection

from networkx.utils.backends import _dispatchable

__all__ = ["bethe_hessian_matrix"]

@_dispatchable
def bethe_hessian_matrix(G, r=None, nodelist: Collection[Incomplete] | None = None): ...
