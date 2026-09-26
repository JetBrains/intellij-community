from _typeshed import Incomplete

from networkx.classes.digraph import DiGraph
from networkx.classes.graph import _Node
from networkx.utils.backends import _dispatchable

__all__ = ["tree_data", "tree_graph"]

def tree_data(G: DiGraph[_Node], root, ident: str = "id", children: str = "children") -> dict[Incomplete, Incomplete]: ...
@_dispatchable
def tree_graph(data: dict[Incomplete, Incomplete], ident: str = "id", children: str = "children") -> DiGraph[Incomplete]: ...
