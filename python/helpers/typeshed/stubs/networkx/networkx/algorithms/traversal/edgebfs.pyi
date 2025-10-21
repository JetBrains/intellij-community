from _typeshed import Incomplete
from collections.abc import Generator
from typing import Final

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["edge_bfs"]

FORWARD: Final = "forward"
REVERSE: Final = "reverse"

@_dispatchable
def edge_bfs(G: Graph[_Node], source=None, orientation=None) -> Generator[Incomplete, None, None]: ...
