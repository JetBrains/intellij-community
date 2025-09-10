from collections.abc import Hashable
from functools import cached_property
from typing import Any, ClassVar, overload
from typing_extensions import TypeAlias, TypeVar

from networkx.classes.coreviews import MultiAdjacencyView
from networkx.classes.graph import Graph, _MapFactory, _Node
from networkx.classes.multidigraph import MultiDiGraph
from networkx.classes.reportviews import MultiEdgeView

_MultiEdge: TypeAlias = tuple[_Node, _Node, int]  # noqa: Y047

_DefaultT = TypeVar("_DefaultT")
_KeyT = TypeVar("_KeyT", bound=Hashable)

__all__ = ["MultiGraph"]

class MultiGraph(Graph[_Node]):
    edge_key_dict_factory: ClassVar[_MapFactory]
    def __init__(self, incoming_graph_data=None, multigraph_input: bool | None = None, **attr: Any) -> None: ...
    @cached_property
    def adj(self) -> MultiAdjacencyView[_Node, _Node, dict[str, Any]]: ...  # data can be any type
    def new_edge_key(self, u: _Node, v: _Node) -> int: ...
    @overload  # type: ignore[override]  # Has an additional `key` keyword argument
    def add_edge(self, u_for_edge: _Node, v_for_edge: _Node, key: int | None = None, **attr: Any) -> int: ...
    @overload
    def add_edge(self, u_for_edge: _Node, v_for_edge: _Node, key: _KeyT, **attr: Any) -> _KeyT: ...
    # key : hashable identifier, optional (default=lowest unused integer)
    def remove_edge(self, u: _Node, v: _Node, key: Hashable | None = None) -> None: ...
    def has_edge(self, u: _Node, v: _Node, key: Hashable | None = None) -> bool: ...
    @overload  # type: ignore[override]
    def get_edge_data(
        self, u: _Node, v: _Node, key: Hashable, default: _DefaultT | None = None
    ) -> dict[str, Any] | _DefaultT: ...
    # key : hashable identifier, optional (default=None).
    # default : any Python object (default=None). Value to return if the specific edge (u, v, key) is not found.
    # Returns: The edge attribute dictionary.
    @overload
    def get_edge_data(
        self, u: _Node, v: _Node, key: None = None, default: _DefaultT | None = None
    ) -> dict[Hashable, dict[str, Any] | _DefaultT]: ...
    # default : any Python object (default=None). Value to return if there are no edges between u and v and no key is specified.
    # Returns: A dictionary mapping edge keys to attribute dictionaries for each of those edges if no specific key is provided.
    def copy(self, as_view: bool = False) -> MultiGraph[_Node]: ...
    def to_directed(self, as_view: bool = False) -> MultiDiGraph[_Node]: ...
    def to_undirected(self, as_view: bool = False) -> MultiGraph[_Node]: ...
    def number_of_edges(self, u: _Node | None = None, v: _Node | None = None) -> int: ...
    @cached_property
    def edges(self) -> MultiEdgeView[_Node]: ...  # type: ignore[override]
    # Returns: MultiEdgeView
