from collections.abc import Iterator
from functools import cached_property
from typing import Any
from typing_extensions import Self

from networkx.classes.coreviews import AdjacencyView
from networkx.classes.graph import Graph, _Node
from networkx.classes.reportviews import (
    DiDegreeView,
    InDegreeView,
    InEdgeView,
    InMultiDegreeView,
    OutDegreeView,
    OutEdgeView,
    OutMultiDegreeView,
)

__all__ = ["DiGraph"]

class DiGraph(Graph[_Node]):
    @cached_property
    def succ(self) -> AdjacencyView[_Node, _Node, dict[str, Any]]: ...
    @cached_property
    def pred(self) -> AdjacencyView[_Node, _Node, dict[str, Any]]: ...
    def has_successor(self, u: _Node, v: _Node) -> bool: ...
    def has_predecessor(self, u: _Node, v: _Node) -> bool: ...
    def successors(self, n: _Node) -> Iterator[_Node]: ...

    neighbors = successors

    def predecessors(self, n: _Node) -> Iterator[_Node]: ...
    @cached_property
    def out_edges(self) -> OutEdgeView[_Node]: ...
    @cached_property
    def in_edges(self) -> InEdgeView[_Node]: ...
    @cached_property
    def in_degree(self) -> InDegreeView[_Node] | InMultiDegreeView[_Node]: ...
    @cached_property
    def out_degree(self) -> OutDegreeView[_Node] | OutMultiDegreeView[_Node]: ...
    def to_undirected(self, reciprocal: bool = False, as_view: bool = False) -> Graph[_Node]: ...  # type: ignore[override]
    # reciprocal : If True, only edges that appear in both directions ... will be kept in the undirected graph.
    def reverse(self, copy: bool = True) -> Self: ...
    @cached_property
    def edges(self) -> OutEdgeView[_Node]: ...  # type: ignore[override] # An OutEdgeView of the DiGraph as G.edges or G.edges().
    @cached_property
    def degree(self) -> int | DiDegreeView[_Node]: ...  # type: ignore[override] # Returns DiDegreeView or int
