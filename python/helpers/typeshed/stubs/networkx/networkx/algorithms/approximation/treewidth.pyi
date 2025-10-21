from _typeshed import Incomplete

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["treewidth_min_degree", "treewidth_min_fill_in"]

@_dispatchable
def treewidth_min_degree(G: Graph[_Node]): ...
@_dispatchable
def treewidth_min_fill_in(G: Graph[_Node]): ...

class MinDegreeHeuristic:
    count: Incomplete

    def __init__(self, graph) -> None: ...
    def best_node(self, graph): ...

def min_fill_in_heuristic(graph_dict) -> Incomplete | None: ...
@_dispatchable
def treewidth_decomp(G: Graph[_Node], heuristic=...) -> tuple[int, Graph[_Node]]: ...
