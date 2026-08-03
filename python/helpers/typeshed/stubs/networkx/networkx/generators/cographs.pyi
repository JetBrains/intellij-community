from _typeshed import Incomplete

from networkx.classes.graph import Graph
from networkx.utils.backends import _dispatchable

__all__ = ["random_cograph"]

@_dispatchable
def random_cograph(n: int, seed=None) -> Graph[Incomplete]: ...
