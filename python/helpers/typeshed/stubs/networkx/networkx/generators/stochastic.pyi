from networkx.utils.backends import _dispatchable

__all__ = ["stochastic_graph"]

@_dispatchable
def stochastic_graph(G, copy: bool = True, weight: str = "weight"): ...
