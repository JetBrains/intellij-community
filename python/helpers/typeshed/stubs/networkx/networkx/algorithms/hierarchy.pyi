from networkx.utils.backends import _dispatchable

__all__ = ["flow_hierarchy"]

@_dispatchable
def flow_hierarchy(G, weight: str | None = None) -> float: ...
