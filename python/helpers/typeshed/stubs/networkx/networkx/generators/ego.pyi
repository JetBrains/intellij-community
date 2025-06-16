from networkx.utils.backends import _dispatchable

__all__ = ["ego_graph"]

@_dispatchable
def ego_graph(G, n, radius: float = 1, center: bool = True, undirected: bool = False, distance=None): ...
