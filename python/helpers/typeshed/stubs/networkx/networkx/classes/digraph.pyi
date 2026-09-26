from collections.abc import Iterator
from functools import cached_property
from typing import Any
from typing_extensions import Self

from networkx.classes.coreviews import AdjacencyView
from networkx.classes.graph import Graph, _EdgeData, _Node, _NodeData
from networkx.classes.reportviews import (
    DiDegreeView,
    InDegreeView,
    InEdgeView,
    InMultiDegreeView,
    InMultiEdgeView,
    OutDegreeView,
    OutEdgeView,
    OutMultiDegreeView,
)

__all__ = ["DiGraph"]

# NOTE: Graph subclasses relationships are so complex
# we're only overriding methods that differ in signature from the base classes
# to use inheritance to our advantage and reduce complexity
class DiGraph(Graph[_Node, _NodeData, _EdgeData]):
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
    def edges(self) -> OutEdgeView[_Node, _NodeData, _EdgeData]: ...
    @cached_property
    def out_edges(self) -> OutEdgeView[_Node, _NodeData, _EdgeData]: ...
    @cached_property
    # Including subtypes' possible return types for LSP
    def in_edges(self) -> InEdgeView[_Node, _NodeData, _EdgeData] | InMultiEdgeView[_Node, _NodeData, _EdgeData]: ...
    @cached_property
    def degree(self) -> DiDegreeView[_Node, _NodeData, _EdgeData]: ...
    @cached_property
    # Including subtypes' possible return types for LSP
    def in_degree(self) -> InDegreeView[_Node, _NodeData, _EdgeData] | InMultiDegreeView[_Node, _NodeData, _EdgeData]: ...
    @cached_property
    # Including subtypes' possible return types for LSP
    def out_degree(self) -> OutDegreeView[_Node, _NodeData, _EdgeData] | OutMultiDegreeView[_Node, _NodeData, _EdgeData]: ...
    def to_undirected(self, reciprocal: bool = False, as_view: bool = False) -> Graph[_Node, _NodeData, _EdgeData]: ...  # type: ignore[override] # Has an additional `reciprocal` keyword argument
    def reverse(self, copy: bool = True) -> Self: ...
