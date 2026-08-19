from _typeshed import Incomplete

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["stoer_wagner"]

@_dispatchable
def stoer_wagner(
    G: Graph[_Node], weight: str = "weight", heap: type = ...
) -> tuple[int | float, tuple[list[Incomplete], list[Incomplete]]]: ...
