from _typeshed import Incomplete

from networkx.utils.backends import _dispatchable

from ..classes.graph import Graph

__all__ = ["spectral_graph_forge"]

@_dispatchable
def spectral_graph_forge(G, alpha, transformation: str = "identity", seed=None) -> Graph[Incomplete]: ...
