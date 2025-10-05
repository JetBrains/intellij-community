from functools import cached_property
from typing import Any

from networkx.classes.coreviews import MultiAdjacencyView
from networkx.classes.digraph import DiGraph
from networkx.classes.graph import _EdgeWithData, _Node
from networkx.classes.multigraph import MultiGraph
from networkx.classes.reportviews import (
    InMultiDegreeView,
    InMultiEdgeDataView,
    InMultiEdgeView,
    OutMultiDegreeView,
    OutMultiEdgeView,
)

__all__ = ["MultiDiGraph"]

class MultiDiGraph(MultiGraph[_Node], DiGraph[_Node]):
    @cached_property
    def succ(self) -> MultiAdjacencyView[_Node, _Node, dict[str, Any]]: ...
    @cached_property
    def pred(self) -> MultiAdjacencyView[_Node, _Node, dict[str, Any]]: ...
    @cached_property
    def edges(self) -> OutMultiEdgeView[_Node]: ...  # type: ignore[override]
    # Returns: OutMultiEdgeView
    @cached_property
    def out_edges(self) -> OutMultiEdgeView[_Node]: ...
    @cached_property
    def in_edges(self) -> InMultiEdgeView[_Node] | InMultiEdgeDataView[_Node, _EdgeWithData[_Node]]: ...  # type: ignore[override]
    # Returns : InMultiEdgeView or InMultiEdgeDataView
    @cached_property
    def in_degree(self) -> InMultiDegreeView[_Node]: ...
    @cached_property
    def out_degree(self) -> OutMultiDegreeView[_Node]: ...
    def to_undirected(self, reciprocal: bool = False, as_view: bool = False) -> MultiGraph[_Node]: ...  # type: ignore[override]
    def reverse(self, copy: bool = True) -> MultiDiGraph[_Node]: ...
    def copy(self, as_view: bool = False) -> MultiDiGraph[_Node]: ...
