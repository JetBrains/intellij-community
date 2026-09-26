from _typeshed import Incomplete
from collections.abc import Iterable

from networkx.classes.graph import Graph
from networkx.utils.backends import _dispatchable

__all__ = ["interval_graph"]

@_dispatchable
def interval_graph(intervals: Iterable[Incomplete]) -> Graph[Incomplete]: ...
