from _typeshed import Incomplete, StrPath, SupportsRead, SupportsWrite
from collections.abc import Collection, Generator, Iterable

from networkx.classes.graph import Graph, _Node
from networkx.utils.backends import _dispatchable

__all__ = ["generate_edgelist", "write_edgelist", "parse_edgelist", "read_edgelist"]

@_dispatchable
def write_edgelist(
    G: Graph[_Node],
    path: StrPath | SupportsWrite[bytes],
    comments: str = "#",
    delimiter: str = " ",
    data: bool = True,
    encoding: str = "utf-8",
) -> None: ...
@_dispatchable
def generate_edgelist(G: Graph[_Node], delimiter: str = " ", data: bool = True) -> Generator[str]: ...
@_dispatchable
def parse_edgelist(
    lines: Iterable[str],
    comments: str | None = "#",
    delimiter: str | None = None,
    create_using: Graph[_Node] | type[Graph[_Node]] | None = None,
    nodetype: type[Incomplete] | None = None,
    data: bool | Collection[tuple[str, type[Incomplete]]] = True,
) -> Graph[Incomplete]: ...
@_dispatchable
def read_edgelist(
    path: StrPath | SupportsRead[bytes],
    comments: str | None = "#",
    delimiter: str | None = None,
    create_using: Graph[Incomplete] | type[Graph[Incomplete]] | None = None,
    nodetype=None,
    data: bool | Collection[tuple[str, type[Incomplete]]] = True,
    edgetype=None,
    encoding: str | None = "utf-8",
) -> Graph[Incomplete]: ...
