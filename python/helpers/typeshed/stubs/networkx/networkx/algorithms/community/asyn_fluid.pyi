from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable
from numpy.random import RandomState

__all__ = ["asyn_fluidc"]

@_dispatchable
def asyn_fluidc(G: Graph[_Node], k: int, max_iter: int = 100, seed: int | RandomState | None = None): ...
