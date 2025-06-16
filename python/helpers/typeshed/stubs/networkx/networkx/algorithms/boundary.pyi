from _typeshed import Incomplete
from collections.abc import Generator, Iterable
from typing import TypeVar, overload

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

_U = TypeVar("_U")
__all__ = ["edge_boundary", "node_boundary"]

@overload
def edge_boundary(
    G: Graph[_Node],
    nbunch1: Iterable[Incomplete],
    nbunch2: Iterable[Incomplete] | None = None,
    data=False,
    keys: bool = False,
    default=None,
) -> Generator[tuple[_Node, _Node], None, None]: ...
@overload
def edge_boundary(
    G: Graph[_Node],
    nbunch1: Iterable[Incomplete],
    nbunch2: Iterable[Incomplete] | None = None,
    data=False,
    keys: bool = False,
    default=None,
) -> Generator[tuple[_Node, _Node, dict[str, Incomplete]], None, None]: ...
@overload
def edge_boundary(
    G: Graph[_Node],
    nbunch1: Iterable[Incomplete],
    nbunch2: Iterable[Incomplete] | None = None,
    data=False,
    keys: bool = False,
    default=None,
) -> Generator[tuple[_Node, _Node, dict[str, Incomplete]], None, None]: ...
@overload
def edge_boundary(
    G: Graph[_Node],
    nbunch1: Iterable[Incomplete],
    nbunch2: Iterable[Incomplete] | None = None,
    data=False,
    keys: bool = False,
    default: _U | None = None,
) -> Generator[tuple[_Node, _Node, dict[str, _U]], None, None]: ...
@overload
def edge_boundary(
    G: Graph[_Node],
    nbunch1: Iterable[Incomplete],
    nbunch2: Iterable[Incomplete] | None = None,
    data=False,
    keys: bool = False,
    default: _U | None = None,
) -> Generator[tuple[_Node, _Node, dict[str, _U]], None, None]: ...
@overload
def edge_boundary(
    G: Graph[_Node],
    nbunch1: Iterable[Incomplete],
    nbunch2: Iterable[Incomplete] | None = None,
    data=False,
    keys: bool = False,
    default=None,
) -> Generator[tuple[_Node, _Node, int], None, None]: ...
@overload
def edge_boundary(
    G: Graph[_Node],
    nbunch1: Iterable[Incomplete],
    nbunch2: Iterable[Incomplete] | None = None,
    data=False,
    keys: bool = False,
    default=None,
) -> Generator[tuple[_Node, _Node, int], None, None]: ...
@overload
def edge_boundary(
    G: Graph[_Node],
    nbunch1: Iterable[Incomplete],
    nbunch2: Iterable[Incomplete] | None = None,
    data=False,
    keys: bool = False,
    default=None,
) -> Generator[tuple[_Node, _Node, int, dict[str, Incomplete]], None, None]: ...
@overload
def edge_boundary(
    G: Graph[_Node],
    nbunch1: Iterable[Incomplete],
    nbunch2: Iterable[Incomplete] | None = None,
    data=False,
    keys: bool = False,
    default=None,
) -> Generator[tuple[_Node, _Node, int, dict[str, Incomplete]], None, None]: ...
@overload
def edge_boundary(
    G: Graph[_Node],
    nbunch1: Iterable[Incomplete],
    nbunch2: Iterable[Incomplete] | None = None,
    data=False,
    keys: bool = False,
    default: _U | None = None,
) -> Generator[tuple[_Node, _Node, int, dict[str, _U]], None, None]: ...
@overload
def edge_boundary(
    G: Graph[_Node],
    nbunch1: Iterable[Incomplete],
    nbunch2: Iterable[Incomplete] | None = None,
    data=False,
    keys: bool = False,
    default: _U | None = None,
) -> Generator[tuple[_Node, _Node, int, dict[str, _U]], None, None]: ...
@_dispatchable
def node_boundary(G: Graph[_Node], nbunch1: Iterable[Incomplete], nbunch2: Iterable[Incomplete] | None = None) -> set[_Node]: ...
