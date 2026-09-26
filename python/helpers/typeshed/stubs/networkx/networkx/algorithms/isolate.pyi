from _typeshed import Incomplete
from collections.abc import Iterator

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["is_isolate", "isolates", "number_of_isolates"]

@_dispatchable
def is_isolate(G: Graph[_Node], n: _Node) -> bool: ...
@_dispatchable
def isolates(G: Graph[_Node]) -> Iterator[Incomplete]: ...
@_dispatchable
def number_of_isolates(G: Graph[_Node]) -> int: ...
