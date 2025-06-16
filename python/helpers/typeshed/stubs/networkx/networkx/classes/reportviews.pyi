from _typeshed import Incomplete, Unused
from abc import ABC
from collections.abc import Iterable, Iterator, Mapping, Set as AbstractSet
from typing import Any, Generic, Literal, TypeVar, overload
from typing_extensions import Self

from networkx.classes.graph import Graph, _Edge, _NBunch, _Node

_D = TypeVar("_D")
_U = TypeVar("_U")

__all__ = [
    "NodeView",
    "NodeDataView",
    "EdgeView",
    "OutEdgeView",
    "InEdgeView",
    "EdgeDataView",
    "OutEdgeDataView",
    "InEdgeDataView",
    "MultiEdgeView",
    "OutMultiEdgeView",
    "InMultiEdgeView",
    "MultiEdgeDataView",
    "OutMultiEdgeDataView",
    "InMultiEdgeDataView",
    "DegreeView",
    "DiDegreeView",
    "InDegreeView",
    "OutDegreeView",
    "MultiDegreeView",
    "DiMultiDegreeView",
    "InMultiDegreeView",
    "OutMultiDegreeView",
]

class NodeView(Mapping[_Node, dict[str, Any]], AbstractSet[_Node]):
    def __init__(self, graph: Graph[_Node]) -> None: ...
    def __len__(self) -> int: ...
    def __iter__(self) -> Iterator[_Node]: ...
    def __getitem__(self, n: _Node) -> dict[str, Any]: ...
    def __contains__(self, n: object) -> bool: ...
    @overload
    def __call__(self, data: Literal[False] = False, default=None) -> Iterator[_Node]: ...
    @overload
    def __call__(self, data: Literal[True] | str, default=None) -> Iterator[tuple[_Node, dict[str, Any]]]: ...
    def data(self, data: bool | str = True, default=None) -> NodeDataView[_Node]: ...

class NodeDataView(AbstractSet[_Node]):
    def __init__(self, nodedict: Mapping[str, Incomplete], data: bool | str = False, default=None) -> None: ...
    def __len__(self) -> int: ...
    def __iter__(self) -> Iterator[tuple[_Node, Incomplete]]: ...  # type: ignore[override]
    def __contains__(self, n: object) -> bool: ...
    def __getitem__(self, n: _Node): ...

class DiDegreeView(Generic[_Node]):
    def __init__(self, G: Graph[_Node], nbunch: _NBunch[_Node] = None, weight: None | bool | str = None) -> None: ...
    def __call__(self, nbunch: _NBunch[_Node] = None, weight: None | bool | str = None) -> int | DiDegreeView[_Node]: ...
    def __getitem__(self, n: _Node) -> float: ...
    def __iter__(self) -> Iterator[tuple[_Node, float]]: ...
    def __len__(self) -> int: ...

class DegreeView(DiDegreeView[_Node]): ...
class OutDegreeView(DiDegreeView[_Node]): ...
class InDegreeView(DiDegreeView[_Node]): ...
class MultiDegreeView(DiDegreeView[_Node]): ...
class DiMultiDegreeView(DiDegreeView[_Node]): ...
class InMultiDegreeView(DiDegreeView[_Node]): ...
class OutMultiDegreeView(DiDegreeView[_Node]): ...
class EdgeViewABC(ABC): ...

class OutEdgeDataView(EdgeViewABC, Generic[_Node, _D]):
    def __init__(self, viewer, nbunch: _NBunch[_Node] = None, data: bool = False, *, default=None) -> None: ...
    def __len__(self) -> int: ...
    def __iter__(self) -> Iterator[_D]: ...
    def __contains__(self, e: _Edge[_Node]) -> bool: ...

class EdgeDataView(OutEdgeDataView[_Node, _D]): ...
class InEdgeDataView(OutEdgeDataView[_Node, _D]): ...

class OutMultiEdgeDataView(OutEdgeDataView[_Node, _D]):
    keys: bool
    def __init__(
        self, viewer, nbunch: _NBunch[_Node] = None, data: bool = False, *, default=None, keys: bool = False
    ) -> None: ...

class MultiEdgeDataView(OutEdgeDataView[_Node, _D]): ...
class InMultiEdgeDataView(OutEdgeDataView[_Node, _D]): ...

class OutEdgeView(AbstractSet[Incomplete], Mapping[Incomplete, Incomplete], EdgeViewABC, Generic[_Node]):
    def __init__(self, G: Graph[_Node]) -> None: ...
    def __len__(self) -> int: ...
    def __iter__(self) -> Iterator[tuple[_Node, _Node]]: ...
    def __contains__(self, e: _Edge[_Node]) -> bool: ...  # type: ignore[override]
    def __getitem__(self, e: _Edge[_Node]) -> dict[str, Any]: ...
    dataview = OutEdgeDataView
    @overload
    def __call__(self, nbunch: None = None, data: Literal[False] = False, *, default: Unused = None) -> Self: ...  # type: ignore[overload-overlap]
    @overload
    def __call__(
        self, nbunch: _Node | Iterable[_Node], data: Literal[False] = False, *, default: None = None
    ) -> OutEdgeDataView[_Node, tuple[_Node, _Node]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node], data: Literal[True], *, default: None = None
    ) -> OutEdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: Literal[True], default: None = None
    ) -> OutEdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node], data: str, *, default: _U | None = None
    ) -> OutEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: str, default: _U | None = None
    ) -> OutEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload
    def data(self, data: Literal[False], default: Unused = None, nbunch: None = None) -> Self: ...
    @overload
    def data(
        self, data: Literal[True] = True, default: None = None, nbunch: _NBunch[_Node] = None
    ) -> OutEdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def data(
        self, data: str, default: _U | None = None, nbunch: _NBunch[_Node] = None
    ) -> OutEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...

class EdgeView(OutEdgeView[_Node]):
    dataview = EdgeDataView
    # Have to override parent's overloads with the proper return type based on dataview
    @overload
    def __call__(self, nbunch: None = None, data: Literal[False] = False, *, default: Unused = None) -> Self: ...  # type: ignore[overload-overlap]
    @overload
    def __call__(
        self, nbunch: _Node | Iterable[_Node], data: Literal[False] = False, *, default: None = None
    ) -> EdgeDataView[_Node, tuple[_Node, _Node]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node], data: Literal[True], *, default: None = None
    ) -> EdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: Literal[True], default: None = None
    ) -> EdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node], data: str, *, default: _U | None = None
    ) -> EdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: str, default: _U | None = None
    ) -> EdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload
    def data(self, data: Literal[False], default: Unused = None, nbunch: None = None) -> Self: ...
    @overload
    def data(
        self, data: Literal[True] = True, default: None = None, nbunch: _NBunch[_Node] = None
    ) -> EdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def data(
        self, data: str, default: _U | None = None, nbunch: _NBunch[_Node] = None
    ) -> EdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...

class InEdgeView(OutEdgeView[_Node]):
    dataview = InEdgeDataView
    # Have to override parent's overloads with the proper return type based on dataview
    @overload
    def __call__(self, nbunch: None = None, data: Literal[False] = False, *, default: Unused = None) -> Self: ...  # type: ignore[overload-overlap]
    @overload
    def __call__(
        self, nbunch: _Node | Iterable[_Node], data: Literal[False] = False, *, default: None = None
    ) -> InEdgeDataView[_Node, tuple[_Node, _Node]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node], data: Literal[True], *, default: None = None
    ) -> InEdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: Literal[True], default: None = None
    ) -> InEdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node], data: str, *, default: _U | None = None
    ) -> InEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: str, default: _U | None = None
    ) -> InEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload
    def data(self, data: Literal[False], default: Unused = None, nbunch: None = None) -> Self: ...
    @overload
    def data(
        self, data: Literal[True] = True, default: None = None, nbunch: _NBunch[_Node] = None
    ) -> InEdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def data(
        self, data: str, default: _U | None = None, nbunch: _NBunch[_Node] = None
    ) -> InEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...

class OutMultiEdgeView(OutEdgeView[_Node]):
    def __iter__(self) -> Iterator[tuple[_Node, _Node, Incomplete]]: ...  # type: ignore[override]
    def __getitem__(self, e: tuple[_Node, _Node, Incomplete]) -> dict[str, Any]: ...  # type: ignore[override]
    dataview = OutMultiEdgeDataView
    @overload  # type: ignore[override]  # Has an additional `keys` keyword argument
    def __call__(  # type: ignore[overload-overlap]
        self, nbunch: None = None, data: Literal[False] = False, *, default: Unused = None, keys: Literal[True]
    ) -> Self: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, data: Literal[False] = False, *, default: None = None, keys: Literal[False] = False
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node]]: ...
    @overload
    def __call__(
        self, nbunch: _Node | Iterable[_Node], data: Literal[False] = False, *, default: None = None, keys: Literal[True]
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: Literal[True], default: None = None, keys: Literal[True]
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node], data: Literal[True], *, default: None = None, keys: Literal[True]
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: Literal[True], default: None = None, keys: Literal[False] = False
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node], data: str, *, default: _U | None = None, keys: Literal[False] = False
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: str, default: _U | None = None, keys: Literal[True]
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, _U]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: str, default: _U | None = None, keys: Literal[False] = False
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload  # type: ignore[override]
    def data(self, data: Literal[False], default: Unused = None, nbunch: None = None, *, keys: Literal[True]) -> Self: ...
    @overload
    def data(
        self, data: Literal[False], default: None = None, nbunch: _NBunch[_Node] = None, keys: Literal[False] = False
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node]]: ...
    @overload
    def data(
        self, data: Literal[True] = True, default: None = None, nbunch: _NBunch[_Node] = None, keys: Literal[False] = False
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def data(
        self, data: Literal[True] = True, default: None = None, nbunch: _NBunch[_Node] = None, *, keys: Literal[True]
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, dict[str, Incomplete]]]: ...
    @overload
    def data(
        self, data: str, default: _U | None = None, nbunch: _NBunch[_Node] = None, keys: Literal[False] = False
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload
    def data(
        self, data: str, default: _U | None = None, nbunch: _NBunch[_Node] = None, *, keys: Literal[True]
    ) -> OutMultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, _U]]: ...

class MultiEdgeView(OutMultiEdgeView[_Node]):
    dataview = MultiEdgeDataView  # type: ignore[assignment]
    # Have to override parent's overloads with the proper return type based on dataview
    @overload  # type: ignore[override]  # Has an additional `keys` keyword argument
    def __call__(  # type: ignore[overload-overlap]
        self, nbunch: None = None, data: Literal[False] = False, *, default: Unused = None, keys: Literal[True]
    ) -> Self: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, data: Literal[False] = False, *, default: None = None, keys: Literal[False] = False
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node]]: ...
    @overload
    def __call__(
        self, nbunch: _Node | Iterable[_Node], data: Literal[False] = False, *, default: None = None, keys: Literal[True]
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: Literal[True], default: None = None, keys: Literal[True]
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node], data: Literal[True], *, default: None = None, keys: Literal[True]
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: Literal[True], default: None = None, keys: Literal[False] = False
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node], data: str, *, default: _U | None = None, keys: Literal[False] = False
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: str, default: _U | None = None, keys: Literal[True]
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, _U]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: str, default: _U | None = None, keys: Literal[False] = False
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload  # type: ignore[override]
    def data(self, data: Literal[False], default: Unused = None, nbunch: None = None, *, keys: Literal[True]) -> Self: ...
    @overload
    def data(
        self, data: Literal[False], default: None = None, nbunch: _NBunch[_Node] = None, keys: Literal[False] = False
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node]]: ...
    @overload
    def data(
        self, data: Literal[True] = True, default: None = None, nbunch: _NBunch[_Node] = None, keys: Literal[False] = False
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def data(
        self, data: Literal[True] = True, default: None = None, nbunch: _NBunch[_Node] = None, *, keys: Literal[True]
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, dict[str, Incomplete]]]: ...
    @overload
    def data(
        self, data: str, default: _U | None = None, nbunch: _NBunch[_Node] = None, keys: Literal[False] = False
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload
    def data(
        self, data: str, default: _U | None = None, nbunch: _NBunch[_Node] = None, *, keys: Literal[True]
    ) -> MultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, _U]]: ...

class InMultiEdgeView(OutMultiEdgeView[_Node]):
    dataview = InMultiEdgeDataView  # type: ignore[assignment]
    # Have to override parent's overloads with the proper return type based on dataview
    @overload  # type: ignore[override]
    def __call__(  # type: ignore[overload-overlap]
        self, nbunch: None = None, data: Literal[False] = False, *, default: Unused = None, keys: Literal[True]
    ) -> Self: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, data: Literal[False] = False, *, default: None = None, keys: Literal[False] = False
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node]]: ...
    @overload
    def __call__(
        self, nbunch: _Node | Iterable[_Node], data: Literal[False] = False, *, default: None = None, keys: Literal[True]
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: Literal[True], default: None = None, keys: Literal[True]
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node], data: Literal[True], *, default: None = None, keys: Literal[True]
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: Literal[True], default: None = None, keys: Literal[False] = False
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node], data: str, *, default: _U | None = None, keys: Literal[False] = False
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: str, default: _U | None = None, keys: Literal[True]
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, _U]]: ...
    @overload
    def __call__(
        self, nbunch: _NBunch[_Node] = None, *, data: str, default: _U | None = None, keys: Literal[False] = False
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload  # type: ignore[override]
    def data(self, data: Literal[False], default: Unused = None, nbunch: None = None, *, keys: Literal[True]) -> Self: ...
    @overload
    def data(
        self, data: Literal[False], default: None = None, nbunch: _NBunch[_Node] = None, keys: Literal[False] = False
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node]]: ...
    @overload
    def data(
        self, data: Literal[True] = True, default: None = None, nbunch: _NBunch[_Node] = None, keys: Literal[False] = False
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node, dict[str, Incomplete]]]: ...
    @overload
    def data(
        self, data: Literal[True] = True, default: None = None, nbunch: _NBunch[_Node] = None, *, keys: Literal[True]
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, dict[str, Incomplete]]]: ...
    @overload
    def data(
        self, data: str, default: _U | None = None, nbunch: _NBunch[_Node] = None, keys: Literal[False] = False
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node, _U]]: ...
    @overload
    def data(
        self, data: str, default: _U | None = None, nbunch: _NBunch[_Node] = None, *, keys: Literal[True]
    ) -> InMultiEdgeDataView[_Node, tuple[_Node, _Node, Incomplete, _U]]: ...
