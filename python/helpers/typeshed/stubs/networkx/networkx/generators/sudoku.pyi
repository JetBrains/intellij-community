from _typeshed import Incomplete

from networkx.classes.graph import Graph
from networkx.utils.backends import _dispatchable

__all__ = ["sudoku_graph"]

@_dispatchable
def sudoku_graph(n: int = 3) -> Graph[Incomplete]: ...
