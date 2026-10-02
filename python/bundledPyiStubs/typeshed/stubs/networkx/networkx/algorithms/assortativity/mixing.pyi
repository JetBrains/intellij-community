from _typeshed import Incomplete
from collections.abc import Iterable, Mapping

import numpy as np
from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["attribute_mixing_matrix", "attribute_mixing_dict", "degree_mixing_matrix", "degree_mixing_dict", "mixing_dict"]

@_dispatchable
def attribute_mixing_dict(
    G: Graph[_Node], attribute: str, nodes: Iterable[Incomplete] | None = None, normalized: bool = False
) -> dict[Incomplete, Incomplete]: ...
@_dispatchable
def attribute_mixing_matrix(
    G: Graph[_Node],
    attribute: str,
    nodes: Iterable[Incomplete] | None = None,
    mapping: Mapping[Incomplete, Incomplete] | None = None,
    normalized: bool = True,
) -> np.ndarray[Incomplete, Incomplete]: ...
@_dispatchable
def degree_mixing_dict(
    G: Graph[_Node], x: str = "out", y: str = "in", weight: str | None = None, nodes=None, normalized: bool = False
) -> dict[Incomplete, Incomplete]: ...
@_dispatchable
def degree_mixing_matrix(
    G: Graph[_Node],
    x: str = "out",
    y: str = "in",
    weight: str | None = None,
    nodes: Iterable[Incomplete] | None = None,
    normalized: bool = True,
    mapping: Mapping[Incomplete, Incomplete] | None = None,
) -> np.ndarray[Incomplete, Incomplete]: ...
@_dispatchable
def mixing_dict(xy: Iterable[tuple[Incomplete, Incomplete]], normalized: bool = False) -> dict[Incomplete, Incomplete]: ...
