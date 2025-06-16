from collections.abc import Callable
from typing_extensions import TypeAlias

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

_WeightFunction: TypeAlias = Callable[..., int]

__all__ = [
    "eccentricity",
    "diameter",
    "harmonic_diameter",
    "radius",
    "periphery",
    "center",
    "barycenter",
    "resistance_distance",
    "kemeny_constant",
    "effective_graph_resistance",
]

@_dispatchable
def eccentricity(G: Graph[_Node], v: _Node | None = None, sp=None, weight: str | _WeightFunction | None = None): ...
@_dispatchable
def diameter(G: Graph[_Node], e=None, usebounds: bool = False, weight: str | _WeightFunction | None = None): ...
@_dispatchable
def harmonic_diameter(G, sp=None, *, weight: str | _WeightFunction | None = None) -> float: ...
@_dispatchable
def periphery(G: Graph[_Node], e=None, usebounds: bool = False, weight: str | _WeightFunction | None = None): ...
@_dispatchable
def radius(G: Graph[_Node], e=None, usebounds: bool = False, weight: str | _WeightFunction | None = None): ...
@_dispatchable
def center(G: Graph[_Node], e=None, usebounds: bool = False, weight: str | _WeightFunction | None = None): ...
@_dispatchable
def barycenter(G, weight: str | _WeightFunction | None = None, attr=None, sp=None): ...
@_dispatchable
def resistance_distance(G: Graph[_Node], nodeA=None, nodeB=None, weight: str | None = None, invert_weight: bool = True): ...
@_dispatchable
def effective_graph_resistance(G, weight: str | None = None, invert_weight: bool = True) -> float: ...
@_dispatchable
def kemeny_constant(G, *, weight: str | None = None) -> float: ...
