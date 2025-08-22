from networkx.classes.graph import _Node
from networkx.utils.backends import _dispatchable

__all__ = ["efficiency", "local_efficiency", "global_efficiency"]

@_dispatchable
def efficiency(G, u: _Node, v: _Node) -> float: ...
@_dispatchable
def global_efficiency(G) -> float: ...
@_dispatchable
def local_efficiency(G) -> float: ...
