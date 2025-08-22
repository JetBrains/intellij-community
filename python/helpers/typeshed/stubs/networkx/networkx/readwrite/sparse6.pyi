from _typeshed import Incomplete

from networkx.utils.backends import _dispatchable

from ..classes.graph import Graph

__all__ = ["from_sparse6_bytes", "read_sparse6", "to_sparse6_bytes", "write_sparse6"]

@_dispatchable
def from_sparse6_bytes(string) -> Graph[Incomplete]: ...
def to_sparse6_bytes(G, nodes=None, header: bool = True): ...
@_dispatchable
def read_sparse6(path): ...
def write_sparse6(G, path, nodes=None, header: bool = True) -> None: ...
