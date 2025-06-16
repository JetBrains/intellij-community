from _typeshed import Incomplete
from collections.abc import Generator

from networkx.classes.digraph import DiGraph
from networkx.classes.graph import _Node
from networkx.classes.multidigraph import MultiDiGraph
from networkx.utils.backends import _dispatchable

__all__ = ["number_attracting_components", "attracting_components", "is_attracting_component"]

@_dispatchable
def attracting_components(G) -> Generator[Incomplete, None, None]: ...
@_dispatchable
def number_attracting_components(G): ...
@_dispatchable
def is_attracting_component(G: DiGraph[_Node] | MultiDiGraph[_Node]) -> bool: ...
