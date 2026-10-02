from _typeshed import Incomplete, StrPath, SupportsRead, SupportsWrite
from collections.abc import Iterable

from networkx.classes.graph import Graph, _Node
from networkx.classes.multigraph import MultiGraph
from networkx.utils.backends import _dispatchable

__all__ = ["from_sparse6_bytes", "read_sparse6", "to_sparse6_bytes", "write_sparse6"]

@_dispatchable
def from_sparse6_bytes(string: str) -> Graph[Incomplete]: ...
def to_sparse6_bytes(G: Graph[_Node], nodes: Iterable[Incomplete] | None = None, header: bool = True): ...
@_dispatchable
def read_sparse6(path: StrPath | SupportsRead[bytes]) -> MultiGraph[Incomplete]: ...
def write_sparse6(
    G: Graph[_Node], path: StrPath | SupportsWrite[bytes], nodes: Iterable[Incomplete] | None = None, header: bool = True
) -> None: ...
