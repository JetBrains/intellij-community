from _typeshed import Incomplete

from networkx.utils.backends import _dispatchable

from ..classes.multidigraph import MultiDiGraph

def write_p2g(G, path, encoding: str = "utf-8") -> None: ...
@_dispatchable
def read_p2g(path, encoding: str = "utf-8") -> MultiDiGraph[Incomplete]: ...
@_dispatchable
def parse_p2g(lines) -> MultiDiGraph[Incomplete]: ...
