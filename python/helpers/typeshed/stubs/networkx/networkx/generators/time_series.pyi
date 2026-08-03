from _typeshed import Incomplete
from collections.abc import Sequence

from networkx.classes.graph import Graph
from networkx.utils.backends import _dispatchable

__all__ = ["visibility_graph"]

@_dispatchable
def visibility_graph(series: Sequence[float]) -> Graph[Incomplete]: ...
